#!/usr/bin/env bash
#
# Cross-builds libzephyr-java for Android from pinned sources, one library per ABI, with
# zephyr's own contrib/depends (NDK r18b, API 21, the package versions zephyr pins).
#
# 1. Checks that the zephyr-cpp submodule is at the pinned sha and places the zephyr tree at
#    external/zephyr-cpp/external/zephyr by sha, exactly as bin/build_libzephyr_java.sh does,
#    including the verification of the pin tag when it is present locally.
# 2. Applies the build-layer patches under patches/depends/ to the zephyr tree, in name order,
#    with patch -p1. They touch build tooling under contrib/depends/ only, never zephyr or
#    zephyr-cpp sources (register D21; each patch has a DEP-3 header). Idempotent: a patch that
#    is already applied is skipped; one that neither applies nor is applied stops the build.
# 3. Builds contrib/depends for the ABI's host triplet into contrib/depends/<triplet>/ (this
#    downloads the NDK and the package sources; depends checks their sha256).
# 4. Builds zephyr's library targets with the depends toolchain in build/<triplet>/release of the
#    zephyr tree. cmake is skipped when that directory has a CMakeCache.txt; make always runs.
# 5. Builds the JNI library in build-android/<abi>/ (CMakeLists.txt of this repo, Android branch)
#    and copies it to build/android/<abi>/libzephyr-java.so.
#
# Usage: bin/build_android.sh <arm64-v8a|armeabi-v7a|x86_64|--all>
#   JAVA_HOME must point at a JDK: its include/ and include/linux/ provide jni.h for every ABI.

set -euo pipefail

usage() {
  echo "usage: $0 <arm64-v8a|armeabi-v7a|x86_64|--all>" >&2
  exit 2
}

[ $# -eq 1 ] || usage
case "$1" in
  --all) ABIS="arm64-v8a armeabi-v7a x86_64" ;;
  arm64-v8a|armeabi-v7a|x86_64) ABIS="$1" ;;
  *) usage ;;
esac

: "${JAVA_HOME:?set JAVA_HOME to a JDK (its include/ provides jni.h)}"

# ABI -> depends host triplet
triplet_of() {
  case "$1" in
    arm64-v8a) echo aarch64-linux-android ;;
    armeabi-v7a) echo arm-linux-androideabi ;;
    x86_64) echo x86_64-linux-android ;;
  esac
}

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

# 1. zephyr-cpp pin and the zephyr tree (the same steps as bin/build_libzephyr_java.sh)
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

for ABI in $ABIS; do
  TRIPLET="$(triplet_of "$ABI")"
  echo "=== $ABI ($TRIPLET)"

  # 3. depends for this host
  make -C "$Z/contrib/depends" HOST="$TRIPLET" -j"$(nproc)"
  TOOLCHAIN="$ROOT/$Z/contrib/depends/$TRIPLET/share/toolchain.cmake"

  # 4. zephyr's library targets, as in bin/build_libzephyr_java.sh (no blocks, no executables)
  ZB="$Z/build/$TRIPLET/release"
  mkdir -p "$ZB" && cd "$ZB"
  if [ -f CMakeCache.txt ]; then
    echo "zephyr: $ZB/CMakeCache.txt exists, skipping cmake"
  else
    cmake -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN" -DBUILD_TESTS=OFF -DUSE_CCACHE=ON ../../..
  fi
  make -j"$(nproc)" wallet cryptonote_protocol
  cd "$ROOT"

  # every static library zephyr-cpp imports must exist (libwallet-crypto.a is optional,
  # libblocks.a is never built, see bin/build_libzephyr_java.sh)
  MISSING=0
  for LIB in $(grep -o '\${MONERO_PROJECT_BUILD}/[^)[:space:]]*\.a' external/zephyr-cpp/CMakeLists.txt \
               | grep -v -e '/libwallet-crypto\.a$' -e '/libblocks\.a$' | sort -u); do
    F="$ZB/${LIB#\$\{MONERO_PROJECT_BUILD\}/}"
    if [ ! -f "$F" ]; then
      echo "missing zephyr library: $F" >&2
      MISSING=1
    fi
  done
  [ "$MISSING" = 0 ] || exit 1

  # 5. JNI library
  mkdir -p "build-android/$ABI" && cd "build-android/$ABI"
  cmake -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN" -DANDROID_HOST="$TRIPLET" ../..
  cmake --build . -j"$(nproc)"
  cd "$ROOT"
  mkdir -p "build/android/$ABI"
  cp "build-android/$ABI/libzephyr-java.so" "build/android/$ABI/libzephyr-java.so"
  echo "built build/android/$ABI/libzephyr-java.so"
done
