#!/usr/bin/env bash
#
# Merges the Maven repository of the previous release into the site of this one, so the Maven
# repository on Pages keeps every release: dist/<version>/site/maven/ then serves this version
# and every earlier one side by side, with one maven-metadata.xml per artifactId listing them all.
#
# Input: bin/previous-release.env, a pinned input like bin/pins.env:
#   PREV_VERSION      the previous release, e.g. 2.3.0-sec.2-java.1
#   PREV_SITE_URL     its release asset site-<PREV_VERSION>.tar.gz on GitHub
#   PREV_SITE_SHA256  that archive's sha256, from the previous release's signed SHA256SUMS
# The previous archive holds every release before it (each release merged its predecessor), so
# one archive is enough.
#
# Steps:
#  1. the archive: PREV_SITE_ARCHIVE (a local copy) when set, else a download of PREV_SITE_URL;
#     its sha256 must be PREV_SITE_SHA256 (a mismatch stops the script, exit 1)
#  2. extraction into a temporary directory; every entry must be a file or directory under site/
#  3. every version directory of the previous site/maven/ is copied into this site/maven/
#     unchanged (byte for byte, its checksum files included, checked after the copy); a version
#     that this repository already has stops the script, and the previous maven-metadata.xml must
#     list exactly the version directories the archive holds
#  4. each artifactId's maven-metadata.xml is written anew: every version in version order,
#     <latest> and <release> the newest (this version for the artifactIds this build publishes),
#     <lastUpdated> from SOURCE_DATE_EPOCH; its .md5, .sha1, .sha256 (and .sha512 where Gradle
#     wrote one) rewritten; then every checksum file of the repository is checked
#  5. index.html gets a section "Older releases" before "Documentation": each older version
#     with its Maven coordinates and release page, and the note that javadoc/ is this version's
#     only (an older version's Javadoc is its -javadoc.jar)
# Versions must follow the release scheme <x>.<y>.<z>-sec.<N>-java.<M> (docs: RELEASES.md §2);
# they are ordered by their numbers, which is Maven's and Gradle's order for this scheme.
#
# bin/package_release.sh calls it after bin/make_site.sh and before the site archive, the manifest
# and SHA256SUMS, so CI and a local build merge the same pinned input; bin/make_manifest.sh
# records the merged release as previous_release.
#
# Environment:
#   PREV_SITE_ARCHIVE  optional: a local copy of the previous site archive (checked like a
#                      download); bin/package_release_container.sh mounts it into the container
#   SOURCE_DATE_EPOCH  optional: the commit time of this checkout unless set
#   TMPDIR             where the archive is downloaded and extracted (default /tmp)
#
# Usage: bin/merge_previous_release.sh [dist/<version>]   (default: dist/<version of pom.xml>)

set -euo pipefail

cd "$(dirname "$0")/.."
ROOT="$(pwd)"

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
[ -f "$SITE/index.html" ] && [ -d "$SITE/maven" ] || { echo "no $SITE with index.html and maven/: run bin/make_site.sh first" >&2; exit 1; }

[ -f bin/previous-release.env ] || { echo "no bin/previous-release.env" >&2; exit 1; }
# shellcheck source=previous-release.env
. bin/previous-release.env
: "${PREV_VERSION:?bin/previous-release.env: no PREV_VERSION}"
: "${PREV_SITE_URL:?bin/previous-release.env: no PREV_SITE_URL}"
: "${PREV_SITE_SHA256:?bin/previous-release.env: no PREV_SITE_SHA256}"
[ "$PREV_VERSION" != "$VERSION" ] || { echo "bin/previous-release.env names this version, $VERSION, as the previous one" >&2; exit 1; }

WORK="$(mktemp -d "${TMPDIR:-/tmp}/merge-previous-release.XXXXXX")"
trap 'rm -rf "$WORK"' EXIT

# 1: the archive, checked
if [ -n "${PREV_SITE_ARCHIVE:-}" ]; then
  [ -f "$PREV_SITE_ARCHIVE" ] || { echo "PREV_SITE_ARCHIVE $PREV_SITE_ARCHIVE is not a file" >&2; exit 1; }
  ARCHIVE="$PREV_SITE_ARCHIVE"
  echo "previous release $PREV_VERSION: $ARCHIVE (PREV_SITE_ARCHIVE)"
else
  ARCHIVE="$WORK/site-$PREV_VERSION.tar.gz"
  echo "previous release $PREV_VERSION: downloading $PREV_SITE_URL"
  curl --location --fail --silent --show-error --connect-timeout 30 --retry 5 \
       --speed-limit 1000 --speed-time 120 -o "$ARCHIVE" "$PREV_SITE_URL"
fi
SUM="$(sha256sum "$ARCHIVE" | cut -d' ' -f1)"
if [ "$SUM" != "$PREV_SITE_SHA256" ]; then
  echo "sha256 mismatch for the site archive of $PREV_VERSION: $SUM, expected $PREV_SITE_SHA256" >&2
  exit 1
fi
echo "  sha256 $SUM: ok"

# 2: extraction; only plain files and directories under site/
LISTING="$(tar -tvzf "$ARCHIVE")"
BAD="$(awk '{ t = substr($1, 1, 1); n = $NF; if ((t != "-" && t != "d") || n !~ /^site(\/|$)/ || n ~ /(^|\/)\.\.(\/|$)/) print }' <<< "$LISTING")"
[ -z "$BAD" ] || { printf '%s\n' "$BAD" >&2; echo "unexpected entries in the site archive of $PREV_VERSION (above)" >&2; exit 1; }
mkdir "$WORK/prev"
tar -xzf "$ARCHIVE" -C "$WORK/prev" --no-same-owner
[ -d "$WORK/prev/site/maven" ] || { echo "the site archive of $PREV_VERSION has no site/maven/" >&2; exit 1; }

# 3 to 5
python3 -I - "$WORK/prev/site/maven" "$SITE" "$VERSION" "$PREV_VERSION" <<'PY'
import filecmp
import hashlib
import os
import re
import shutil
import sys
import time
import xml.etree.ElementTree as ET

PREV_REPO, SITE, VERSION, PREV_VERSION = sys.argv[1:5]
REPO = os.path.join(SITE, "maven")
GROUP_PATH = "io/github/loki-notfound"
GROUP_ID = GROUP_PATH.replace("/", ".")
SCHEME = re.compile(r"^(\d+)\.(\d+)\.(\d+)-sec\.(\d+)-java\.(\d+)$")
CHECKSUMS = (".md5", ".sha1", ".sha256", ".sha512")
LAST_UPDATED = time.strftime("%Y%m%d%H%M%S", time.gmtime(int(os.environ["SOURCE_DATE_EPOCH"])))


def fail(msg):
    sys.exit(msg)


def order(v):
    m = SCHEME.match(v)
    if not m:
        fail(f"version {v} does not follow the release scheme <x>.<y>.<z>-sec.<N>-java.<M>")
    return tuple(int(x) for x in m.groups())


def digest(path, alg):
    h = hashlib.new(alg)
    with open(path, "rb") as f:
        for block in iter(lambda: f.read(1 << 20), b""):
            h.update(block)
    return h.hexdigest()


def metadata_versions(path):
    root = ET.parse(path).getroot()
    return [v.text.strip() for v in root.findall("versioning/versions/version")]


def version_dirs(artifact_dir):
    return sorted(d for d in os.listdir(artifact_dir)
                  if os.path.isdir(os.path.join(artifact_dir, d)))


def artifact_ids(repo):
    base = os.path.join(repo, GROUP_PATH)
    if not os.path.isdir(base):
        return []
    return sorted(a for a in os.listdir(base)
                  if os.path.isfile(os.path.join(base, a, "maven-metadata.xml")))


order(VERSION)
order(PREV_VERSION)
prev_ids = artifact_ids(PREV_REPO)
new_ids = artifact_ids(REPO)
if not prev_ids:
    fail(f"the site archive of {PREV_VERSION} has no artifact under maven/{GROUP_PATH}/")

# 3: the previous version directories, copied unchanged
copied = 0
for aid in prev_ids:
    src_dir = os.path.join(PREV_REPO, GROUP_PATH, aid)
    listed = sorted(metadata_versions(os.path.join(src_dir, "maven-metadata.xml")))
    dirs = version_dirs(src_dir)
    if listed != dirs:
        fail(f"{aid} of {PREV_VERSION}: maven-metadata.xml lists {listed}, the archive holds {dirs}")
    if PREV_VERSION not in dirs and aid in new_ids:
        fail(f"{aid}: the site archive of {PREV_VERSION} does not hold {PREV_VERSION}")
    for v in dirs:
        order(v)
        dest = os.path.join(REPO, GROUP_PATH, aid, v)
        if os.path.exists(dest):
            fail(f"{aid} {v} is in this repository already: not overwriting it")
        os.makedirs(os.path.dirname(dest), exist_ok=True)
        # copyfile: contents only, modes from the umask (bin/package_release.sh: 022)
        shutil.copytree(os.path.join(src_dir, v), dest, copy_function=shutil.copyfile)
        for dirpath, _, files in os.walk(os.path.join(src_dir, v)):
            for name in files:
                a = os.path.join(dirpath, name)
                b = os.path.join(dest, os.path.relpath(a, os.path.join(src_dir, v)))
                if not filecmp.cmp(a, b, shallow=False):
                    fail(f"copy of {a} differs")
                copied += 1
        print(f"  {aid} {v}: copied")

# 4: maven-metadata.xml of every artifactId with all its versions
for aid in sorted(set(prev_ids) | set(new_ids)):
    adir = os.path.join(REPO, GROUP_PATH, aid)
    versions = sorted(version_dirs(adir), key=order)
    newest = versions[-1]
    if aid in new_ids and newest != VERSION:
        fail(f"{aid}: {newest} is newer than this release, {VERSION}")
    meta = os.path.join(adir, "maven-metadata.xml")
    lines = ['<?xml version="1.0" encoding="UTF-8"?>', "<metadata>",
             f"  <groupId>{GROUP_ID}</groupId>", f"  <artifactId>{aid}</artifactId>",
             "  <versioning>", f"    <latest>{newest}</latest>", f"    <release>{newest}</release>",
             "    <versions>"]
    lines += [f"      <version>{v}</version>" for v in versions]
    lines += ["    </versions>", f"    <lastUpdated>{LAST_UPDATED}</lastUpdated>", "  </versioning>",
              "</metadata>"]
    with open(meta, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lines) + "\n")
    for ext in CHECKSUMS:
        if ext == ".sha512" and not os.path.exists(meta + ext):
            continue
        with open(meta + ext, "w", encoding="ascii", newline="\n") as f:
            f.write(digest(meta, ext[1:]) + "\n")
    print(f"  {aid}: maven-metadata.xml lists {', '.join(versions)}; latest and release {newest}")

# every checksum file of the repository against its file
checked = 0
for dirpath, _, files in os.walk(REPO):
    for name in sorted(files):
        path = os.path.join(dirpath, name)
        for ext in CHECKSUMS:
            if name.endswith(ext):
                target = path[: -len(ext)]
                if not os.path.isfile(target):
                    fail(f"{path} has no file")
                with open(path, encoding="ascii") as f:
                    if f.read().split()[0] != digest(target, ext[1:]):
                        fail(f"wrong {path}")
                checked += 1
print(f"  {copied} files copied byte for byte, {checked} checksum files checked")

# 5: index.html, the older releases
older = {}
for aid in sorted(set(prev_ids) | set(new_ids)):
    for v in version_dirs(os.path.join(REPO, GROUP_PATH, aid)):
        if v != VERSION:
            older.setdefault(v, []).append(aid)
items = []
for v in sorted(older, key=order, reverse=True):
    # the Android artifact first, as in the sections above
    coords = ", ".join(f"<code>{GROUP_ID}:{aid}:{v}</code>"
                       for aid in sorted(older[v], key=lambda a: (not a.endswith("-android"), a)))
    items.append(f'<li>{v} (<a href="https://github.com/loki-notfound/myzephyr-java/releases/tag/v{v}">'
                 f"release</a>): {coords}</li>")
section = f"""<h2>Older releases</h2>
<p>The Maven repository keeps every release, so a build can stay on an older version with the same
repository line. The <a href="javadoc/index.html">Javadoc</a> on this site is that of version
{VERSION} only; an older version's Javadoc is its <code>zephyr-java-&lt;version&gt;-javadoc.jar</code>,
in the repository and among the files of its release.</p>
<ul>
{chr(10).join(items)}
</ul>

"""
index = os.path.join(SITE, "index.html")
with open(index, encoding="utf-8") as f:
    html = f.read()
anchor = "<h2>Documentation</h2>\n"
if html.count(anchor) != 1 or "<h2>Older releases</h2>" in html:
    fail(f"{index}: expected one '<h2>Documentation</h2>' line and no 'Older releases' section yet")
with open(index, "w", encoding="utf-8", newline="\n") as f:
    f.write(html.replace(anchor, section + anchor))
print(f"  index.html: {len(items)} older release(s) listed")
PY

echo "merged the Maven repository of $PREV_VERSION into ${SITE#"$ROOT"/}/maven"
