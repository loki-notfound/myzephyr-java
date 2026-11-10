#!/usr/bin/env bash
#
# The root certificates shipped in the library, src/main/resources-raw/zephyr/common/cacert.pem:
# Mozilla's root certificates as curl publishes them (https://curl.se/docs/caextract.html), pinned
# by date, URL and sha256 in bin/ca-bundle.env. zephyr.common.ZephyrTls reads the file as a
# resource; it is the default trust of the native wallet on Android.
#
#   bin/update_ca_bundle.sh                 downloads CA_BUNDLE_URL, fails unless its sha256 is
#                                           CA_BUNDLE_SHA256, and writes the file (a temporary
#                                           name in the same directory, then renamed)
#   bin/update_ca_bundle.sh --check-latest  changes nothing: reads
#                                           https://curl.se/ca/cacert.pem.sha256 and exits 1,
#                                           naming both hashes, when the newest published file
#                                           is not the pinned one (exit 2: no answer)
#
# A build never runs this script: builds use the committed file only and stay reproducible
# (bin/check_versions.sh checks that the file matches CA_BUNDLE_SHA256). The release commit and
# the release checklist run --check-latest: every release ships the current root certificates;
# a newer file means moving the three lines of bin/ca-bundle.env to it and running this script.

set -euo pipefail

cd "$(dirname "$0")/.."
# shellcheck source=ca-bundle.env
. bin/ca-bundle.env
OUT=src/main/resources-raw/zephyr/common/cacert.pem
LATEST_URL=https://curl.se/ca/cacert.pem.sha256
CURL=(curl --fail --silent --show-error --proto '=https' --connect-timeout 30 --max-time 300)

case "${1:-}" in
  --check-latest)
    if ! ANSWER="$("${CURL[@]}" "$LATEST_URL")"; then
      echo "no answer from $LATEST_URL" >&2
      exit 2
    fi
    LATEST="${ANSWER%% *}"
    if ! [[ "$LATEST" =~ ^[0-9a-f]{64}$ ]]; then
      echo "unexpected answer from $LATEST_URL: '$ANSWER'" >&2
      exit 2
    fi
    if [ "$LATEST" != "$CA_BUNDLE_SHA256" ]; then
      echo "curl publishes newer root certificates: newest $LATEST, pinned $CA_BUNDLE_SHA256 ($CA_BUNDLE_DATE)" >&2
      exit 1
    fi
    echo "the pinned root certificates are the newest curl publishes: $CA_BUNDLE_DATE, $CA_BUNDLE_SHA256"
    exit 0
    ;;
  "") ;;
  *)
    echo "usage: $0 [--check-latest]" >&2
    exit 2
    ;;
esac

mkdir -p "$(dirname "$OUT")"
TMP="$(mktemp "$OUT.XXXXXX")"
trap 'rm -f "$TMP"' EXIT
"${CURL[@]}" -o "$TMP" "$CA_BUNDLE_URL"
GOT="$(sha256sum "$TMP" | cut -d' ' -f1)"
if [ "$GOT" != "$CA_BUNDLE_SHA256" ]; then
  echo "$CA_BUNDLE_URL: sha256 $GOT, expected $CA_BUNDLE_SHA256" >&2
  exit 1
fi
chmod 644 "$TMP"
mv "$TMP" "$OUT"
trap - EXIT
echo "$OUT: $CA_BUNDLE_DATE, $(grep -c -- '-----BEGIN CERTIFICATE-----' "$OUT") certificates, sha256 $GOT"
