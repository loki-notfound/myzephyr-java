#!/usr/bin/env bash
#
# Checks a built site (site/check_site.py): a page for every guide file; every page well-formed,
# with the strict policy, one level-1 heading and no skipped heading level, unique ids and the skip
# link; no family bar, script, style element or attribute, event handler, external resource or
# absolute path; http:// nowhere outside code and https:// only as a link target; callouts in the
# house markup, at most two per guide page; every relative link and anchor resolves (into javadoc/
# and maven/ when the site has them); the stylesheets load nothing from elsewhere. Works on the
# guide-only build of bin/build_site.sh and on a release's site of bin/make_site.sh. Needs python3
# (standard library only).
#
# Usage: bin/check_site.sh [<site directory>]   (default build/site)

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
[ $# -le 1 ] || { echo "usage: $0 [<site directory>]" >&2; exit 2; }
SITE="${1:-$ROOT/build/site}"
[ -d "$SITE" ] || { echo "no site directory $SITE" >&2; exit 1; }
SITE="$(cd "$SITE" && pwd)"

cd "$ROOT"
python3 -I site/check_site.py --site "$SITE"
