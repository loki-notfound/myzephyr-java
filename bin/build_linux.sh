#!/usr/bin/env bash
#
# Builds libzephyr-java for Linux from pinned sources with zephyr's own contrib/depends, one
# library per architecture, linked against the static depends libraries only (boost, OpenSSL,
# unbound, expat, sodium, hidapi, libusb, eudev, protobuf) and the static libstdc++ and libgcc,
# so the library needs nothing from the host but the C runtime. This is the release build of the
# Linux natives; bin/build_libzephyr_java.sh stays the quick developer build against the system
# libraries.
#
# 1. Checks that the zephyr-cpp submodule is at the pinned sha and places the zephyr tree at
#    external/zephyr-cpp/external/zephyr by sha, exactly as bin/build_android.sh does,
#    including the verification of the pin tag when it is present locally.
# 2. Applies the build-layer patches under patches/depends/ to the zephyr tree, in name order,
#    with patch -p1, idempotently (the same loop as bin/build_android.sh, so both scripts leave
#    the tree in the same state). On a Linux host:
#      0001-android-ndk-python3     no-op (android_ndk is not a Linux package)
#      0002-boost-1.64-b2-engine    needed (the host gcc 14 compiles boost's build engine)
#      0003-android-ndk-x86_64      no-op (android_ndk is not a Linux package)
#      0004-openssl-openssldir      needed (OPENSSLDIR /etc/ssl instead of the depends prefix)
# 3. Builds contrib/depends for the host triplet into contrib/depends/<triplet>/ (this downloads
#    the package sources; depends checks their sha256).
#      0005-neutral-engines-and-unbound-sysconfdir  needed (no depends-prefix paths in OpenSSL
#                                                   and unbound)
# 4. Builds zephyr's library targets with the depends toolchain in build/<triplet>/release of the
#    zephyr tree (configured afresh every time; make rebuilds only what changed).
# 5. Builds the JNI library in build-linux/<arch>/ (CMakeLists.txt of this repo, with
#    DEPENDS_PREFIX set, configured afresh every time) and copies it to
#    build/natives/linux-<arch>/libzephyr-java.so, with build/natives/linux-<arch>/build-env
#    (container image or none, glibc, compiler) for the manifest.
# The release natives are built in the debian:12 image: bin/build_linux_container.sh runs this
# script there; run directly, it builds on the host.
#
# Reproducibility: SOURCE_DATE_EPOCH is exported (the commit time of this checkout unless set),
# and zephyr and the JNI library are compiled with -ffile-prefix-map=<this checkout>=. and
# -ffile-prefix-map=<depends prefix>=/depends (gcc: __FILE__ and debug information), so the
# library does not depend on where the checkout is.
#
# Usage: bin/build_linux.sh <x86_64|arm64|--all>
#   x86_64  depends host x86_64-linux-gnu (the host compiler)
#   arm64   depends host aarch64-linux-gnu (aarch64-linux-gnu-gcc/g++ and binutils)
#   --all   both, or the architectures in BUILD_LINUX_ARCHES (for example "x86_64")
#   JAVA_HOME must point at a JDK: its include/ and include/linux/ provide jni.h.

set -euo pipefail

usage() {
  echo "usage: $0 <x86_64|arm64|--all>" >&2
  exit 2
}

[ $# -eq 1 ] || usage
case "$1" in
  --all) ARCHES="${BUILD_LINUX_ARCHES:-x86_64 arm64}" ;;
  x86_64|arm64) ARCHES="$1" ;;
  *) usage ;;
esac

: "${JAVA_HOME:?set JAVA_HOME to a JDK (its include/ provides jni.h)}"
for ARCH in $ARCHES; do
  case "$ARCH" in x86_64|arm64) ;; *) echo "unknown architecture in BUILD_LINUX_ARCHES: $ARCH" >&2; exit 2 ;; esac
done

# architecture -> depends host triplet
triplet_of() {
  case "$1" in
    x86_64) echo x86_64-linux-gnu ;;
    arm64) echo aarch64-linux-gnu ;;
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

# 1. zephyr-cpp pin and the zephyr tree (the same steps as bin/build_android.sh)
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

for ARCH in $ARCHES; do
  TRIPLET="$(triplet_of "$ARCH")"
  PREFIX="$ROOT/$Z/contrib/depends/$TRIPLET"
  echo "=== linux-$ARCH ($TRIPLET)"

  # 3. depends for this host
  make -C "$Z/contrib/depends" HOST="$TRIPLET" -j"$(nproc)"
  TOOLCHAIN="$PREFIX/share/toolchain.cmake"
  # gcc: -ffile-prefix-map covers __FILE__ and the debug information; the later map wins for
  # the depends prefix, which lies inside the checkout
  PREFIX_MAPS="-ffile-prefix-map=$ROOT=. -ffile-prefix-map=$PREFIX=/depends"

  # 4. zephyr's library targets, as in bin/build_android.sh (no blocks, no executables)
  ZB="$Z/build/$TRIPLET/release"
  # A fresh configure every time, with the prefix maps in CFLAGS/CXXFLAGS: CMake adds them to the
  # platform's initial flags (on Android -funwind-tables -no-canonical-prefixes, on armeabi-v7a
  # also -march, -marm, -mfpu, -mfloat-abi), which -DCMAKE_C_FLAGS on a first configure would
  # replace; zephyr's CMakeLists.txt only appends to CMAKE_C_FLAGS/CMAKE_CXX_FLAGS. Objects whose
  # flags did not change are not rebuilt.
  mkdir -p "$ZB" && cd "$ZB"
  rm -f CMakeCache.txt
  CFLAGS="$PREFIX_MAPS" CXXFLAGS="$PREFIX_MAPS" cmake -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN" -DCMAKE_BUILD_TYPE=Release -DUSE_CCACHE=ON ../../..
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

  # 5. JNI library; configured afresh: host and container builds (bin/build_linux_container.sh)
  # share this directory, and its CMake cache records the checkout path (/work in the container)
  mkdir -p "build-linux/$ARCH" && cd "build-linux/$ARCH"
  rm -f CMakeCache.txt
  cmake -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN" -DDEPENDS_PREFIX="$PREFIX" \
    -DCMAKE_C_FLAGS="$PREFIX_MAPS" -DCMAKE_CXX_FLAGS="$PREFIX_MAPS" ../..
  cmake --build . -j"$(nproc)"
  cd "$ROOT"
  mkdir -p "build/natives/linux-$ARCH"
  cp "build-linux/$ARCH/libzephyr-java.so" "build/natives/linux-$ARCH/libzephyr-java.so"
  # the build environment, for bin/make_manifest.sh: the container image (set by
  # bin/build_linux_container.sh) or none, the C library and the compiler (not packaged)
  {
    echo "container=${ZEPHYR_JAVA_CONTAINER:-none}"
    echo "glibc=$(ldd --version | head -1)"
    echo "compiler=$(case "$ARCH" in x86_64) g++ --version ;; arm64) aarch64-linux-gnu-g++ --version ;; esac | head -1)"
  } > "build/natives/linux-$ARCH/build-env"
  echo "built build/natives/linux-$ARCH/libzephyr-java.so ($(tr '\n' ' ' < "build/natives/linux-$ARCH/build-env"))"
done
