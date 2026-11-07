#!/usr/bin/env bash
#
# Builds libzephyr-java for Linux from pinned sources with zephyr's own contrib/depends, one
# library per architecture, linked against the static depends libraries only (boost, OpenSSL,
# unbound, expat, sodium, hidapi, libusb, eudev, protobuf) and the static libstdc++ and libgcc,
# so the library needs nothing from the host but the C runtime. This is the release build of the
# Linux natives; bin/build_libzephyr_java.sh stays the quick developer build against the system
# libraries.
#
# 1. bin/place_zephyr_tree.sh: checks that the zephyr-cpp submodule is at the pinned sha and
#    places the zephyr tree at external/zephyr-cpp/external/zephyr by sha, with the verification
#    of the pin tag when it is present locally (the same script as bin/build_android.sh and the
#    CI package job).
# 2. The same script applies the build-layer patches under patches/depends/ to the zephyr tree,
#    idempotently. On a Linux host:
#      0001-android-ndk-python3     no-op (android_ndk is not a Linux package)
#      0002-boost-1.64-b2-engine    needed (the host gcc 14 compiles boost's build engine)
#      0003-android-ndk-x86_64      no-op (android_ndk is not a Linux package)
#      0004-openssl-openssldir      needed (OPENSSLDIR /etc/ssl instead of the depends prefix)
# 3. Builds contrib/depends for the host triplet into contrib/depends/<triplet>/ (this downloads
#    the package sources; depends checks their sha256). A stalled download fails after 120 s below
#    1000 bytes/s, and depends retries it from downloads.getmonero.org.
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
# Host and container builds share the checkout under different paths (/work in the container).
# A zephyr build directory or build-linux/<arch>/ whose CMake cache records another source path
# is wiped before configuring. A switch between host and container also evicts the depends
# package cache of the other side (depends' funcs.mk removes a package's cache directory before
# storing a new build), so each switch costs a depends rebuild. Releases build Linux in the
# container only.
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

# SOURCE_DATE_EPOCH (reproducible builds): the commit time of this checkout unless it is set
if [ -z "${SOURCE_DATE_EPOCH:-}" ]; then
  SOURCE_DATE_EPOCH="$(git -C "$ROOT" log -1 --format=%ct HEAD)"
fi
export SOURCE_DATE_EPOCH
echo "SOURCE_DATE_EPOCH=$SOURCE_DATE_EPOCH"

# 1, 2: the pinned zephyr tree with the build-layer patches (shared with the other build script
# and the CI package job)
bin/place_zephyr_tree.sh
Z=external/zephyr-cpp/external/zephyr

# Wipes a CMake build directory whose top-level CMakeCache.txt records a source directory other
# than the given one: host and container builds share the checkout under different paths, and
# nested ExternalProject caches (zephyr's translations) record the path too, so a stale tree from
# another path cannot be reconfigured in place.
wipe_if_other_source() {
  local DIR="$1" SRC="$2" HOME_DIR
  [ -f "$DIR/CMakeCache.txt" ] || return 0
  HOME_DIR="$(sed -n 's/^CMAKE_HOME_DIRECTORY:INTERNAL=//p' "$DIR/CMakeCache.txt")"
  if [ "$HOME_DIR" != "$SRC" ]; then
    echo "$DIR: configured for $HOME_DIR, not $SRC: wiped"
    rm -rf "$DIR"
  fi
}

for ARCH in $ARCHES; do
  TRIPLET="$(triplet_of "$ARCH")"
  PREFIX="$ROOT/$Z/contrib/depends/$TRIPLET"
  echo "=== linux-$ARCH ($TRIPLET)"

  # 3. depends for this host. The download command is depends' own (builders/linux.mk, build_os
  # is the build machine's, linux) plus a low-speed limit: a fetch that stays below 1000 bytes/s
  # for 120 s fails instead of hanging, and depends then falls back to downloads.getmonero.org,
  # whose archives it checks by sha256 like the primary ones. $(...) is left
  # to make, so depends' DOWNLOAD_CONNECT_TIMEOUT and DOWNLOAD_RETRIES still apply.
  # shellcheck disable=SC2016
  make -C "$Z/contrib/depends" HOST="$TRIPLET" -j"$(nproc)" \
    build_linux_DOWNLOAD='curl --location --fail --connect-timeout $(DOWNLOAD_CONNECT_TIMEOUT) --retry $(DOWNLOAD_RETRIES) --speed-limit 1000 --speed-time 120 -o'
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
  wipe_if_other_source "$ZB" "$ROOT/$Z"
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
  wipe_if_other_source "build-linux/$ARCH" "$ROOT"
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
