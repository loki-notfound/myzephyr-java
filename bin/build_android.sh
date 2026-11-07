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
#    zephyr tree (configured afresh every time; make rebuilds only what changed).
# 5. Builds the JNI library in build-android/<abi>/ (CMakeLists.txt of this repo, Android branch)
#    and copies it to build/android/<abi>/libzephyr-java.so.
# The release natives are built in the debian:12 image at /work: bin/build_android_container.sh
# runs this script there; run directly, it builds on the host.
# Host and container builds share the checkout under different paths (/work in the container).
# A zephyr build directory or build-android/<abi>/ whose CMake cache records another source path
# is wiped before configuring, as in bin/build_linux.sh. A switch between host and container also
# rebuilds the depends packages (their build ids contain the prefix path and the compilers).
#
# Reproducibility: SOURCE_DATE_EPOCH is exported (the commit time of this checkout unless set),
# and zephyr and the JNI library are compiled with -fdebug-prefix-map=<this checkout>=. and
# -fdebug-prefix-map=<depends prefix>=/depends. NDK r18b's clang 7.0.2 has no -ffile-prefix-map
# and no -fmacro-prefix-map, so __FILE__ strings (assertion and exception messages) stay
# absolute paths of the build host: the Android libraries are reproducible for the same build
# path only, until the core moves to a newer NDK (docs RELEASES §7, option 2).
#
# Usage: bin/build_android.sh <arm64-v8a|armeabi-v7a|x86_64|--all>
#   --all  all three ABIs, or the ABIs in BUILD_ANDROID_ABIS (for example "arm64-v8a")
#   JAVA_HOME must point at a JDK: its include/ and include/linux/ provide jni.h for every ABI.

set -euo pipefail

usage() {
  echo "usage: $0 <arm64-v8a|armeabi-v7a|x86_64|--all>" >&2
  exit 2
}

[ $# -eq 1 ] || usage
case "$1" in
  --all) ABIS="${BUILD_ANDROID_ABIS:-arm64-v8a armeabi-v7a x86_64}" ;;
  arm64-v8a|armeabi-v7a|x86_64) ABIS="$1" ;;
  *) usage ;;
esac

: "${JAVA_HOME:?set JAVA_HOME to a JDK (its include/ provides jni.h)}"
for ABI in $ABIS; do
  case "$ABI" in arm64-v8a|armeabi-v7a|x86_64) ;; *) echo "unknown ABI in BUILD_ANDROID_ABIS: $ABI" >&2; exit 2 ;; esac
done

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

# SOURCE_DATE_EPOCH (reproducible builds): the commit time of this checkout unless it is set
if [ -z "${SOURCE_DATE_EPOCH:-}" ]; then
  SOURCE_DATE_EPOCH="$(git -C "$ROOT" log -1 --format=%ct HEAD)"
fi
export SOURCE_DATE_EPOCH
echo "SOURCE_DATE_EPOCH=$SOURCE_DATE_EPOCH"

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

# Wipes a CMake build directory whose top-level CMakeCache.txt records a source directory other
# than the given one: host and container builds share the checkout under different paths, and
# nested ExternalProject caches (zephyr's translations) record the path too, so a stale tree from
# another path cannot be reconfigured in place (the same function as in bin/build_linux.sh).
wipe_if_other_source() {
  local DIR="$1" SRC="$2" HOME_DIR
  [ -f "$DIR/CMakeCache.txt" ] || return 0
  HOME_DIR="$(sed -n 's/^CMAKE_HOME_DIRECTORY:INTERNAL=//p' "$DIR/CMakeCache.txt")"
  if [ "$HOME_DIR" != "$SRC" ]; then
    echo "$DIR: configured for $HOME_DIR, not $SRC: wiped"
    rm -rf "$DIR"
  fi
}

for ABI in $ABIS; do
  TRIPLET="$(triplet_of "$ABI")"
  echo "=== $ABI ($TRIPLET)"

  # 3. depends for this host
  make -C "$Z/contrib/depends" HOST="$TRIPLET" -j"$(nproc)"
  PREFIX="$ROOT/$Z/contrib/depends/$TRIPLET"
  TOOLCHAIN="$PREFIX/share/toolchain.cmake"
  # clang 7.0.2: debug information only, see the header (__FILE__ stays absolute)
  PREFIX_MAPS="-fdebug-prefix-map=$ROOT=. -fdebug-prefix-map=$PREFIX=/depends"

  # 4. zephyr's library targets, as in bin/build_libzephyr_java.sh (no blocks, no executables)
  ZB="$Z/build/$TRIPLET/release"
  # A fresh configure every time, with the prefix maps in CFLAGS/CXXFLAGS: CMake adds them to the
  # platform's initial flags (on Android -funwind-tables -no-canonical-prefixes, on armeabi-v7a
  # also -march, -marm, -mfpu, -mfloat-abi), which -DCMAKE_C_FLAGS on a first configure would
  # replace; zephyr's CMakeLists.txt only appends to CMAKE_C_FLAGS/CMAKE_CXX_FLAGS. Objects whose
  # flags did not change are not rebuilt.
  wipe_if_other_source "$ZB" "$ROOT/$Z"
  mkdir -p "$ZB" && cd "$ZB"
  rm -f CMakeCache.txt
  CFLAGS="$PREFIX_MAPS" CXXFLAGS="$PREFIX_MAPS" cmake -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN" -DBUILD_TESTS=OFF -DUSE_CCACHE=ON ../../..
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

  # 5. JNI library; a cache configured for the other path (host or container) is wiped
  wipe_if_other_source "build-android/$ABI" "$ROOT"
  mkdir -p "build-android/$ABI" && cd "build-android/$ABI"
  cmake -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN" -DANDROID_HOST="$TRIPLET" \
    -DCMAKE_C_FLAGS="$PREFIX_MAPS" -DCMAKE_CXX_FLAGS="$PREFIX_MAPS" ../..
  cmake --build . -j"$(nproc)"
  cd "$ROOT"
  mkdir -p "build/android/$ABI"
  cp "build-android/$ABI/libzephyr-java.so" "build/android/$ABI/libzephyr-java.so"
  echo "built build/android/$ABI/libzephyr-java.so"
done
