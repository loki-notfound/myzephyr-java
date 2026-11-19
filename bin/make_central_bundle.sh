#!/usr/bin/env bash
#
# Builds dist/<version>/central/, the Maven Central bundle of a release before signing: a
# directory in Maven repository layout, from the release files in dist/<version>/:
#   dev/lateo/zephyr-java/<version>/
#     zephyr-java-<version>.jar, -sources.jar, -javadoc.jar, .pom
#   dev/lateo/zephyr-java-android/<version>/
#     zephyr-java-android-<version>.aar, -sources.jar, -javadoc.jar, .pom, .module
# with .md5, .sha1, .sha256 and .sha512 next to every file (plain hex without a file name or a
# newline, as Maven and Gradle write them). Central requires .md5 and .sha1 and accepts the
# other two; it requires no checksum of an .asc file.
#
# Every file but one is a release file of dist/<version>/, byte for byte. The one exception is the
# AAR's Javadoc jar, which Central requires next to every jar or aar of a non-pom packaging: the
# AAR compiles the same sources (src/main/java) as the JAR, so its Javadoc is the same, and the
# file is the JAR's Javadoc jar under the AAR's name (an IDE then shows the Javadoc for either
# artifact). The Gradle module metadata (.module) is published as Gradle wrote it; its file
# entries are checked against the bundle (size, sha512, sha256, sha1, md5). No maven-metadata.xml:
# Central maintains it.
#
# Checks before anything is written: the version is not a -SNAPSHOT (Central refuses one); both
# POMs name dev.lateo (the groupId of pom.xml and android/gradle.properties), their artifactId,
# the version and their packaging (jar, aar); each has the fields Central requires (name,
# description, url, a licence with name and url, a developer, scm with connection,
# developerConnection and url) and no <repositories>; url, licences, developers and scm are the
# same in both. Without an AAR in dist/<version>/ (a build without Android ABIs) the bundle holds
# the JAR alone.
#
# Reproducible: the same dist/<version>/ gives the same files (contents only; modes 0644 and
# 0755), so two builds of a release give the same bundle. bin/package_release.sh calls this
# script after the release files are in place and before the manifest, which lists the bundle's
# files with their sha256 (central_bundle). The directory is not a release asset (SHA256SUMS lists
# the top-level files only): the maintainer signs it with bin/sign_central_bundle.sh, which
# writes the zip that is uploaded to Central.
#
# Usage: bin/make_central_bundle.sh [dist/<version>]   (default: dist/<version of pom.xml>)

set -euo pipefail
umask 022

cd "$(dirname "$0")/.."
ROOT="$(pwd)"

VERSION="$(python3 -I - "$ROOT/pom.xml" <<'PY'
import sys
import xml.etree.ElementTree as ET
ns = {"m": "http://maven.apache.org/POM/4.0.0"}
print(ET.parse(sys.argv[1]).getroot().find("m:version", ns).text.strip())
PY
)"
DIST="${1:-dist/$VERSION}"
[ -d "$DIST" ] || { echo "no $DIST: run bin/build_release.sh first" >&2; exit 1; }
DIST="$(cd "$DIST" && pwd)"
GROUP="$(sed -n 's/^GROUP=//p' android/gradle.properties)"
AAR_ID="$(sed -n 's/^ARTIFACT_ID=//p' android/gradle.properties)"

python3 -I - "$DIST" "$VERSION" "$GROUP" "$AAR_ID" <<'PY'
import hashlib
import json
import os
import shutil
import sys
import xml.etree.ElementTree as ET

DIST, VERSION, GROUP, AAR_ID = sys.argv[1:5]
JAR_ID = "zephyr-java"
OUT = os.path.join(DIST, "central")
NS = {"m": "http://maven.apache.org/POM/4.0.0"}
ALGS = ("md5", "sha1", "sha256", "sha512")
problems = []


def fail(msg):
    problems.append(msg)


def digest(path, alg):
    h = hashlib.new(alg)
    with open(path, "rb") as f:
        for block in iter(lambda: f.read(1 << 20), b""):
            h.update(block)
    return h.hexdigest()


def text(root, path):
    e = root.find(path, NS)
    return e.text.strip() if e is not None and e.text and e.text.strip() else None


def pom_fields(path, artifact_id, packaging):
    """Central's required fields of one POM, checked; the shared ones returned for comparison."""
    where = os.path.basename(path)
    root = ET.parse(path).getroot()
    for field, want in (("m:groupId", GROUP), ("m:artifactId", artifact_id), ("m:version", VERSION),
                        ("m:packaging", packaging)):
        got = text(root, field)
        if got != want:
            fail(f"{where}: {field[2:]} is {got!r}, expected {want!r}")
    for field in ("m:name", "m:description", "m:url"):
        if not text(root, field):
            fail(f"{where}: no {field[2:]}")
    licences = [(text(l, "m:name"), text(l, "m:url")) for l in root.findall("m:licenses/m:license", NS)]
    if not licences or not all(n and u for n, u in licences):
        fail(f"{where}: no licence with name and url")
    developers = [(text(d, "m:name"), text(d, "m:email"), text(d, "m:url"))
                  for d in root.findall("m:developers/m:developer", NS)]
    if not developers or not all(d[0] for d in developers):
        fail(f"{where}: no developer with a name")
    scm = tuple(text(root, f"m:scm/m:{f}") for f in ("connection", "developerConnection", "url"))
    if not all(scm):
        fail(f"{where}: scm needs connection, developerConnection and url, has {scm}")
    if root.find("m:repositories", NS) is not None:
        fail(f"{where}: a <repositories> element (a Central POM resolves from Central)")
    return {"url": text(root, "m:url"), "licences": licences, "developers": developers, "scm": scm}


if VERSION.endswith("-SNAPSHOT"):
    fail(f"version {VERSION} is a SNAPSHOT: Maven Central accepts release versions only")

jar = f"{JAR_ID}-{VERSION}"
aar = f"{AAR_ID}-{VERSION}"
# (artifactId, [(name in the bundle, source file in dist/)])
components = [(JAR_ID, [(f"{jar}.jar", f"{jar}.jar"), (f"{jar}-sources.jar", f"{jar}-sources.jar"),
                        (f"{jar}-javadoc.jar", f"{jar}-javadoc.jar"), (f"{jar}.pom", f"{jar}.pom")])]
with_aar = os.path.isfile(os.path.join(DIST, f"{aar}.aar"))
if with_aar:
    components.append((AAR_ID, [(f"{aar}.aar", f"{aar}.aar"), (f"{aar}-sources.jar", f"{aar}-sources.jar"),
                                (f"{aar}-javadoc.jar", f"{jar}-javadoc.jar"), (f"{aar}.pom", f"{aar}.pom"),
                                (f"{aar}.module", f"{aar}.module")]))
else:
    print(f"no {aar}.aar in {DIST}: the bundle holds {JAR_ID} only")
for _, files in components:
    for _, src in files:
        if not os.path.isfile(os.path.join(DIST, src)):
            fail(f"missing {os.path.join(DIST, src)}")
if problems:
    sys.exit("\n".join(problems))

shared = {JAR_ID: pom_fields(os.path.join(DIST, f"{jar}.pom"), JAR_ID, "jar")}
if with_aar:
    shared[AAR_ID] = pom_fields(os.path.join(DIST, f"{aar}.pom"), AAR_ID, "aar")
    for key in ("url", "licences", "developers", "scm"):
        if shared[JAR_ID][key] != shared[AAR_ID][key]:
            fail(f"the POMs differ in {key}: {shared[JAR_ID][key]} and {shared[AAR_ID][key]}")
if problems:
    sys.exit("\n".join(problems))

shutil.rmtree(OUT, ignore_errors=True)
group_path = GROUP.replace(".", "/")
count = 0
for artifact_id, files in components:
    vdir = os.path.join(OUT, group_path, artifact_id, VERSION)
    os.makedirs(vdir)
    for name, src in files:
        dest = os.path.join(vdir, name)
        shutil.copyfile(os.path.join(DIST, src), dest)
        os.chmod(dest, 0o644)
        for alg in ALGS:
            with open(f"{dest}.{alg}", "w", encoding="ascii", newline="") as f:
                f.write(digest(dest, alg))
            os.chmod(f"{dest}.{alg}", 0o644)
        count += 1
for dirpath, _, _ in os.walk(OUT):
    os.chmod(dirpath, 0o755)

# the module metadata against the files it names
if with_aar:
    vdir = os.path.join(OUT, group_path, AAR_ID, VERSION)
    with open(os.path.join(vdir, f"{aar}.module"), encoding="utf-8") as f:
        module = json.load(f)
    comp = module.get("component", {})
    if (comp.get("group"), comp.get("module"), comp.get("version")) != (GROUP, AAR_ID, VERSION):
        fail(f"{aar}.module names {comp}")
    named = set()
    for variant in module.get("variants", []):
        for entry in variant.get("files", []):
            path = os.path.join(vdir, entry["url"])
            named.add(entry["url"])
            if not os.path.isfile(path):
                fail(f"{aar}.module names {entry['url']}, which the bundle does not hold")
                continue
            if entry.get("size") != os.path.getsize(path):
                fail(f"{aar}.module: size of {entry['url']}")
            for alg in ALGS:
                if alg in entry and entry[alg] != digest(path, alg):
                    fail(f"{aar}.module: {alg} of {entry['url']}")
    print(f"{aar}.module: {len(named)} files named, all in the bundle with their size and digests")
if problems:
    shutil.rmtree(OUT, ignore_errors=True)
    sys.exit("\n".join(problems))

total = 0
for dirpath, dirnames, filenames in os.walk(OUT):
    dirnames.sort()
    for name in sorted(filenames):
        p = os.path.join(dirpath, name)
        total += 1
        if not name.endswith(tuple("." + a for a in ALGS)):
            print(f"  {os.path.relpath(p, OUT)} {os.path.getsize(p)} {digest(p, 'sha256')}")
print(f"wrote {OUT}: {len(components)} components, {count} files, each with {len(ALGS)} checksums "
      f"({total} files in all)")
PY
