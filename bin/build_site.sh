#!/usr/bin/env bash
#
# Builds the guide-only site (site/build_site.py) into a directory: index.html (the landing page),
# guide/ (one page per file of docs/guide/), assets/ (the house stylesheet, the site's own
# additions and the favicons), .nojekyll and CNAME (the Pages domain, java-docs.lateo.dev);
# relative links only, no JavaScript. For a quick look at the guide, and for publishing the guide
# on Pages apart from a release next to the javadoc/ that Pages already serves.
# bin/make_site.sh builds the same pages into a release's site, with javadoc/.
#
# Needs python3 (standard library only) and git for the footer (the version of pom.xml and the
# commit, with "with local changes" when docs/guide/ or site/ differ from it; without git or a
# repository of its own the footer says that the tree has no commit); nothing is installed.
# Outputs of an earlier build in the directory are replaced, anything else is kept.
#
# Usage: bin/build_site.sh [<output directory>]   (default build/site)

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
[ $# -le 1 ] || { echo "usage: $0 [<output directory>]" >&2; exit 2; }
case "${1:-}" in -*) echo "usage: $0 [<output directory>]" >&2; exit 2 ;; esac
OUT="${1:-$ROOT/build/site}"
mkdir -p "$OUT"
OUT="$(cd "$OUT" && pwd)"

cd "$ROOT"
python3 -I site/build_site.py --out "$OUT"
