#!/usr/bin/env bash
#
# Places the pinned zephyr tree and applies the build-layer patches, the first two steps of every
# depends build, shared by bin/build_linux.sh, bin/build_android.sh and the CI package job (which
# needs the tree for bin/make_manifest.sh: the depends package recipes and android_ndk.mk;
# QUESTIONS R13c-5):
#
# 1. Checks that the zephyr-cpp submodule is at the pinned sha (bin/pins.env) and places the
#    zephyr tree at external/zephyr-cpp/external/zephyr by sha (git init, fetch --depth 1 of the
#    sha, checkout, its own submodules), never from a branch, with the verification of the pin
#    tag when it is present locally; external/zephyr goes into the submodule's info/exclude, so
#    the tree does not show as a change of zephyr-cpp.
# 2. Applies the build-layer patches under patches/depends/ to the zephyr tree, in name order,
#    with patch -p1. They touch build tooling under contrib/depends/ only, never zephyr or
#    zephyr-cpp sources (register D21; each patch has a DEP-3 header). Idempotent: a patch that
#    is already applied is skipped; one that neither applies nor is applied stops the script.
#      0001-android-ndk-python3                     NDK r18b's toolchain script with python3
#      0002-boost-1.64-b2-engine-declarations       boost's build engine under gcc 14
#      0003-android-ndk-x86_64                      the x86_64 standalone toolchain
#      0004-openssl-openssldir                      OPENSSLDIR /etc/ssl
#      0005-neutral-engines-and-unbound-sysconfdir  no depends-prefix paths in OpenSSL, unbound
#    On a Linux build 0001 and 0003 are no-ops (android_ndk is not a Linux package); they are
#    applied all the same, so every build leaves the tree in the same state.
#
# Usage: bin/place_zephyr_tree.sh

set -euo pipefail

cd "$(dirname "$0")/.."
ROOT="$(pwd)"
# shellcheck source=pins.env
. bin/pins.env

# Verifies the pin tag of a tree when it is present locally (PLAN §2): the tag must point at the
# pinned commit and carry a good signature. Without gpg or without the release key in the keyring
# this is a one-line warning, so a build without the keys still works; a bad signature is an error.
verify_pin_tag() {
  local TREE="$1" SHA="$2" OUT
  git -C "$TREE" rev-parse -q --verify "refs/tags/$PIN_TAG" >/dev/null || return 0
  if [ "$(git -C "$TREE" rev-parse "$PIN_TAG^{commit}")" != "$SHA" ]; then
    echo "$TREE: tag $PIN_TAG does not point at the pin $SHA" >&2
    exit 1
  fi
  if ! command -v gpg >/dev/null; then
    echo "warning: $TREE: tag $PIN_TAG not verified, gpg is not installed" >&2
  elif OUT="$(git -C "$TREE" verify-tag --raw "$PIN_TAG" 2>&1)"; then
    echo "$TREE: tag $PIN_TAG verified"
  elif printf '%s\n' "$OUT" | grep -q '^\[GNUPG:\] NO_PUBKEY '; then
    echo "warning: $TREE: tag $PIN_TAG not verified, the release key is not in the keyring" >&2
  else
    printf '%s\n' "$OUT" >&2
    echo "$TREE: tag $PIN_TAG failed verification" >&2
    exit 1
  fi
}

# 1. zephyr-cpp pin and the zephyr tree
CPP_HEAD="$(git -C external/zephyr-cpp rev-parse HEAD 2>/dev/null || true)"
if [ "$CPP_HEAD" != "$ZEPHYR_CPP_SHA" ]; then
  echo "external/zephyr-cpp is at '${CPP_HEAD}', expected ${ZEPHYR_CPP_SHA}." >&2
  echo "Initialise the submodule first: git submodule update --init" >&2
  exit 1
fi
verify_pin_tag external/zephyr-cpp "$ZEPHYR_CPP_SHA"

Z=external/zephyr-cpp/external/zephyr
if [ ! -d "$Z/.git" ]; then
  git init -q "$Z" && git -C "$Z" remote add origin "$ZEPHYR_URL"
fi
if [ "$(git -C "$Z" rev-parse HEAD 2>/dev/null)" != "$ZEPHYR_SHA" ]; then
  git -C "$Z" fetch -q --depth 1 origin "$ZEPHYR_SHA" && git -C "$Z" checkout -q FETCH_HEAD
fi
git -C "$Z" submodule update -q --init --recursive --depth 1
[ "$(git -C "$Z" rev-parse HEAD)" = "$ZEPHYR_SHA" ] || { echo "zephyr tree is not at $ZEPHYR_SHA"; exit 1; }
verify_pin_tag "$Z" "$ZEPHYR_SHA"

EXCLUDE="$(git -C external/zephyr-cpp rev-parse --path-format=absolute --git-path info/exclude)"
mkdir -p "$(dirname "$EXCLUDE")"
grep -qxF 'external/zephyr' "$EXCLUDE" 2>/dev/null || echo 'external/zephyr' >> "$EXCLUDE"

# 2. build-layer patches (D21), idempotent: an applied patch reverses cleanly in a dry run
for P in "$ROOT"/patches/depends/*.patch; do
  [ -e "$P" ] || continue
  if patch -d "$Z" -p1 -R --dry-run -s -f < "$P" >/dev/null 2>&1; then
    echo "patch $(basename "$P"): already applied"
  else
    patch -d "$Z" -p1 --forward < "$P"
  fi
done
echo "zephyr tree $Z at $ZEPHYR_SHA, patches applied"
