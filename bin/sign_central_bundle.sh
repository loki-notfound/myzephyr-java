#!/usr/bin/env bash
#
# Signs the Maven Central bundle of a release and writes the one zip that is uploaded to Central.
# The maintainer runs it, on the machine that holds the maintainer key, after the release build
# has been verified (two builds with the same SHA256SUMS) and before the upload:
#   1. checks dist/<version>/central/ (bin/make_central_bundle.sh, which bin/build_release.sh
#      runs): every .md5, .sha1, .sha256 and .sha512 matches its file, and the bundle holds no
#      .asc yet
#   2. checks that the signing key has its secret key in the keyring (GNUPGHOME or ~/.gnupg) and
#      is neither expired nor revoked; otherwise it refuses and signs nothing
#   3. copies the bundle to a new staging directory and writes an armored detached signature
#      <file>.asc next to every file there except the checksum files (Central requires an .asc per
#      file and no .asc of a checksum file), with gpg --local-user <key>; gpg-agent asks for the
#      passphrase as for any signature
#   4. verifies every signature (gpg --verify, made by the expected key: the primary key's
#      fingerprint in VALIDSIG)
#   5. writes the zip: every file of the staging directory under its repository path
#      (dev/lateo/...), sorted, with the times of SOURCE_DATE_EPOCH (the commit time of this
#      checkout unless set), refused at 1 GB or more (Central's limit); prints its size, sha256
#      and the file count
# dist/<version>/central/ stays unsigned and unchanged; the staging directory is removed. The zip
# is not a release asset of the GitHub release (the release carries the same jars, AAR and POMs,
# and SHA256SUMS with its signature); it goes to the Central Portal (Publish Component), which
# validates it before it is published.
#
# Options:
#   --dry-run          steps 1 and 2 only: prints the files it would sign and the zip it would
#                      write, signs nothing and writes nothing; exits 1 where a real run would
#                      refuse (a bad checksum, an .asc in the bundle, no secret key)
#   --key <fpr>        the signing key's fingerprint (default: the maintainer key
#                      31DA39AB43E0CA67F1F3A661BA2AFF22DF8A4A13, which signs the tags and
#                      SHA256SUMS; its public key is KEYS.asc and on keys.openpgp.org)
#   --out <file>       the zip (default: dist/central-bundle-<version>.zip, beside the release
#                      directory, so that it is never taken for a release asset); an existing
#                      file is refused
# Environment: GNUPGHOME (gpg's), SOURCE_DATE_EPOCH (optional).
#
# Usage: bin/sign_central_bundle.sh [--dry-run] [--key <fpr>] [--out <file>] [dist/<version>]
#        (default: dist/<version of pom.xml>)

set -euo pipefail
umask 022

MAINTAINER_KEY=31DA39AB43E0CA67F1F3A661BA2AFF22DF8A4A13
usage() {
  echo "usage: $0 [--dry-run] [--key <fpr>] [--out <file>] [dist/<version>]" >&2
  exit 2
}

DRY_RUN=0
KEY="$MAINTAINER_KEY"
OUT=""
DIST=""
while [ $# -gt 0 ]; do
  case "$1" in
    --dry-run) DRY_RUN=1; shift ;;
    --key) [ $# -ge 2 ] || usage; KEY="$2"; shift 2 ;;
    --out) [ $# -ge 2 ] || usage; OUT="$2"; shift 2 ;;
    -*) usage ;;
    *) [ -z "$DIST" ] || usage; DIST="$1"; shift ;;
  esac
done
KEY="$(printf '%s' "$KEY" | tr -d ' ' | tr '[:lower:]' '[:upper:]')"
[[ "$KEY" =~ ^[0-9A-F]{40}$ ]] || { echo "--key needs a full 40-hex fingerprint, not '$KEY'" >&2; exit 2; }

START="$(pwd)"
cd "$(dirname "$0")/.."
ROOT="$(pwd)"
command -v gpg >/dev/null || { echo "gpg is not installed: nothing signed" >&2; exit 1; }

VERSION="$(python3 -I - "$ROOT/pom.xml" <<'PY'
import sys
import xml.etree.ElementTree as ET
ns = {"m": "http://maven.apache.org/POM/4.0.0"}
print(ET.parse(sys.argv[1]).getroot().find("m:version", ns).text.strip())
PY
)"
DIST="${DIST:-dist/$VERSION}"
case "$DIST" in /*) ;; *) [ -d "$DIST" ] || DIST="$START/$DIST" ;; esac
BUNDLE="$DIST/central"
[ -d "$BUNDLE" ] || { echo "no $BUNDLE: run bin/make_central_bundle.sh (bin/build_release.sh does)" >&2; exit 1; }
BUNDLE="$(cd "$BUNDLE" && pwd)"
if [ -z "$OUT" ]; then
  OUT="$(cd "$BUNDLE/../.." && pwd)/central-bundle-$VERSION.zip"
else
  case "$OUT" in /*) ;; *) OUT="$START/$OUT" ;; esac
fi
if [ -z "${SOURCE_DATE_EPOCH:-}" ]; then
  SOURCE_DATE_EPOCH="$(git log -1 --format=%ct HEAD)"
fi
export SOURCE_DATE_EPOCH

echo "bundle   $BUNDLE"
echo "key      $KEY"
echo "zip      $OUT"
echo "keyring  ${GNUPGHOME:-~/.gnupg} (GNUPGHOME)"

# 1: the bundle
FILES="$(python3 -I - "$BUNDLE" <<'PY'
import hashlib
import os
import sys

bundle = sys.argv[1]
algs = ("md5", "sha1", "sha256", "sha512")
bad, files = [], []
for dirpath, dirnames, filenames in os.walk(bundle):
    dirnames.sort()
    for name in sorted(filenames):
        rel = os.path.relpath(os.path.join(dirpath, name), bundle)
        if name.endswith(".asc"):
            bad.append(f"{rel}: the bundle is signed already (a fresh bin/make_central_bundle.sh run removes it)")
        elif not name.endswith(tuple("." + a for a in algs)):
            files.append(rel)
            for alg in algs:
                c = os.path.join(dirpath, f"{name}.{alg}")
                if not os.path.isfile(c):
                    bad.append(f"{rel}: no .{alg}")
                    continue
                h = hashlib.new(alg)
                with open(os.path.join(dirpath, name), "rb") as f:
                    h.update(f.read())
                with open(c, encoding="ascii") as f:
                    if f.read().split()[0] != h.hexdigest():
                        bad.append(f"{rel}: wrong .{alg}")
if not files:
    bad.append("no files")
for b in bad:
    print(b, file=sys.stderr)
print("\n".join(files))
sys.exit(1 if bad else 0)
PY
)" || { echo "the bundle failed its checks (above): nothing signed" >&2; exit 1; }
COUNT="$(printf '%s\n' "$FILES" | grep -c .)"
echo "checksums: ok, $COUNT files to sign"

# 2: the key
SEC="$(gpg --batch --with-colons --fixed-list-mode --list-secret-keys "$KEY" 2>/dev/null || true)"
# the validity field of the sec record whose fingerprint (the next fpr record) is the key
VALIDITY="$(printf '%s\n' "$SEC" | awk -F: -v k="$KEY" '
  $1 == "sec" { v = $2; want = 1; next }
  $1 == "fpr" && want { if ($10 == k) print v; want = 0 }' | head -1)"
if [ -z "$VALIDITY" ]; then
  echo "refused: the keyring (GNUPGHOME=${GNUPGHOME:-~/.gnupg}) holds no secret key $KEY: nothing signed" >&2
  exit 1
fi
case "$VALIDITY" in
  e|r|d|i) echo "refused: key $KEY is expired, revoked, disabled or invalid (validity $VALIDITY): nothing signed" >&2; exit 1 ;;
esac
echo "secret key: present ($(gpg --batch --with-colons --list-keys "$KEY" | awk -F: '$1 == "uid" { print $10; exit }'))"

if [ -e "$OUT" ]; then
  echo "refused: $OUT exists" >&2
  exit 1
fi

if [ "$DRY_RUN" = 1 ]; then
  echo "dry run: would sign"
  printf '  %s\n' $FILES
  echo "dry run: would write $OUT with $((COUNT * 6)) files; nothing signed, nothing written"
  exit 0
fi

# 3: the signatures, in a staging copy
STAGE="$(mktemp -d "${TMPDIR:-/tmp}/central-bundle.XXXXXX")"
trap 'rm -rf "$STAGE"' EXIT
cp -R "$BUNDLE"/. "$STAGE"/
while IFS= read -r F; do
  gpg --local-user "$KEY" --armor --detach-sign --output "$STAGE/$F.asc" "$STAGE/$F"
done <<< "$FILES"

# 4: every signature verified, made by the key
while IFS= read -r F; do
  STATUS="$(gpg --batch --status-fd 1 --verify "$STAGE/$F.asc" "$STAGE/$F" 2>/dev/null || true)"
  if ! awk -v k="$KEY" '$1 == "[GNUPG:]" && $2 == "VALIDSIG" && $NF == k { ok = 1 } END { exit !ok }' <<< "$STATUS"; then
    echo "the signature of $F does not verify as made by $KEY: no zip written" >&2
    exit 1
  fi
done <<< "$FILES"
echo "signatures: $COUNT written and verified"

# 5: the zip
python3 -I - "$STAGE" "$OUT" <<'PY'
import os
import sys
import time
import zipfile

stage, out = sys.argv[1:3]
when = time.gmtime(max(int(os.environ["SOURCE_DATE_EPOCH"]), 315532800))[:6]
names = []
for dirpath, dirnames, filenames in os.walk(stage):
    dirnames.sort()
    for name in sorted(filenames):
        names.append(os.path.relpath(os.path.join(dirpath, name), stage).replace(os.sep, "/"))
partial = out + ".partial"
with zipfile.ZipFile(partial, "x", zipfile.ZIP_DEFLATED) as z:
    for rel in sorted(names):
        info = zipfile.ZipInfo(rel, when)
        info.compress_type = zipfile.ZIP_DEFLATED
        info.external_attr = 0o644 << 16
        with open(os.path.join(stage, rel), "rb") as f:
            z.writestr(info, f.read())
size = os.path.getsize(partial)
if size >= 1 << 30:
    os.remove(partial)
    sys.exit(f"the zip has {size} bytes, Central's limit is 1 GB: not written")
with zipfile.ZipFile(partial) as z:
    if z.testzip() is not None:
        os.remove(partial)
        sys.exit("the zip does not read back")
os.rename(partial, out)
print(f"zip: {len(names)} files, {size} bytes")
PY
echo "wrote $OUT"
sha256sum "$OUT"
echo "upload it on the Central Portal (Publish Component); the deployment is published after its validation"
