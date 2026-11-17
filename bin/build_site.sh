#!/usr/bin/env bash
#
# Builds the guide-only site (site/build_site.py) into a directory: index.html (the landing page),
# guide/ (one page per file of docs/guide/), assets/ (the house stylesheet, the site's own
# additions and the favicons) and .nojekyll; relative links only, no JavaScript. For a quick look
# at the guide, and for publishing the guide on Pages apart from a release next to the javadoc/
# and maven/ that Pages already serves. bin/make_site.sh builds the same pages into a release's
# site, with javadoc/ and maven/.
#
#   --maven <dir>   the maven/ directory of a published site: the index then lists every version
#                   found there other than this one as an older release, the same section that
#                   bin/merge_previous_release.sh gives a release's index
#
# Needs python3 (standard library only) and git for the footer (the version of pom.xml and the
# commit, with "with local changes" when docs/guide/ or site/ differ from it; without git or a
# repository of its own the footer says that the tree has no commit); nothing is installed. Outputs of an earlier build in the directory are replaced, anything else is kept.
#
# Usage: bin/build_site.sh [<output directory>] [--maven <dir>]   (default build/site)

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT=""
MAVEN=()
while [ $# -gt 0 ]; do
  case "$1" in
    --maven)
      [ $# -ge 2 ] || { echo "--maven needs a directory" >&2; exit 2; }
      MAVEN=(--maven "$(cd "$2" && pwd)")
      shift 2
      ;;
    -*)
      echo "usage: $0 [<output directory>] [--maven <dir>]" >&2
      exit 2
      ;;
    *)
      [ -z "$OUT" ] || { echo "usage: $0 [<output directory>] [--maven <dir>]" >&2; exit 2; }
      OUT="$1"
      shift
      ;;
  esac
done
OUT="${OUT:-$ROOT/build/site}"
mkdir -p "$OUT"
OUT="$(cd "$OUT" && pwd)"

cd "$ROOT"
python3 -I site/build_site.py --out "$OUT" "${MAVEN[@]}"
