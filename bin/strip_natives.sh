#!/usr/bin/env bash
#
# Strips the native libraries for release and keeps their debug symbols separately.
#
# For every library that bin/build_linux.sh and bin/build_android.sh placed,
#   build/natives/linux-<x86_64|arm64>/libzephyr-java.so
#   build/android/<arm64-v8a|armeabi-v7a|x86_64>/libzephyr-java.so
# with the binutils of its own target:
#   objcopy --only-keep-debug lib.so build/debug/<platform>/libzephyr-java.so.debug
#   strip --strip-unneeded lib.so
#   objcopy --add-gnu-debuglink=build/debug/<platform>/libzephyr-java.so.debug lib.so
# <platform> is linux-x86_64, linux-arm64, android-arm64-v8a, android-armeabi-v7a, android-x86_64.
# The stripped library replaces the original in place (the AAR and the JAR take them from there).
# Binutils: the host's objcopy/strip for linux-x86_64, aarch64-linux-gnu-objcopy/-strip for
# linux-arm64, and for Android those of the NDK r18b standalone toolchain under the depends
# prefix (contrib/depends/<triplet>/native/bin/<triplet>-objcopy, -strip).
#
# Idempotent: a library that already has a .gnu_debuglink section was stripped by an earlier run
# and is skipped (its debug file is kept); the build scripts copy a fresh unstripped library
# each time they run. A platform whose library does not exist is skipped.
# Afterwards each stripped library is checked: the NEEDED entries and the LOAD alignment are the
# same as before stripping, and so is the set of exported (dynamic) symbols.
#
# Usage: bin/strip_natives.sh

set -euo pipefail

cd "$(dirname "$0")/.."
DEPENDS=external/zephyr-cpp/external/zephyr/contrib/depends

# platform -> "<library> <objcopy> <strip>"
tools_of() {
  case "$1" in
    linux-x86_64) echo "build/natives/linux-x86_64/libzephyr-java.so objcopy strip" ;;
    linux-arm64) echo "build/natives/linux-arm64/libzephyr-java.so aarch64-linux-gnu-objcopy aarch64-linux-gnu-strip" ;;
    android-arm64-v8a) T=aarch64-linux-android ;;
    android-armeabi-v7a) T=arm-linux-androideabi ;;
    android-x86_64) T=x86_64-linux-android ;;
  esac
  case "$1" in
    android-*) echo "build/android/${1#android-}/libzephyr-java.so $DEPENDS/$T/native/bin/$T-objcopy $DEPENDS/$T/native/bin/$T-strip" ;;
  esac
}

summary() {
  # NEEDED entries, LOAD alignments and dynamic symbols: what stripping must not change
  readelf -dW "$1" | grep NEEDED | awk '{print $NF}'
  readelf -lW "$1" | awk '$1 == "LOAD" {print "LOAD align", $NF}'
  nm -D --defined-only "$1" | awk '{print $2, $3}'
}

for PLATFORM in linux-x86_64 linux-arm64 android-arm64-v8a android-armeabi-v7a android-x86_64; do
  read -r LIB OBJCOPY STRIP <<< "$(tools_of "$PLATFORM")"
  if [ ! -f "$LIB" ]; then
    echo "$PLATFORM: no $LIB, skipped"
    continue
  fi
  if readelf -SW "$LIB" | grep -q '\.gnu_debuglink'; then
    echo "$PLATFORM: $LIB is already stripped (has .gnu_debuglink), skipped"
    continue
  fi
  DEBUG="build/debug/$PLATFORM/libzephyr-java.so.debug"
  mkdir -p "$(dirname "$DEBUG")"
  BEFORE="$(summary "$LIB")"
  "$OBJCOPY" --only-keep-debug "$LIB" "$DEBUG"
  "$STRIP" --strip-unneeded "$LIB"
  "$OBJCOPY" --add-gnu-debuglink="$DEBUG" "$LIB"
  AFTER="$(summary "$LIB")"
  if [ "$BEFORE" != "$AFTER" ]; then
    echo "$PLATFORM: NEEDED, LOAD alignment or dynamic symbols changed by stripping" >&2
    diff <(echo "$BEFORE") <(echo "$AFTER") >&2 || true
    exit 1
  fi
  EXPORTS="$(nm -D --defined-only "$LIB" | awk '$2 == "T"' | wc -l)"
  echo "$PLATFORM: stripped $LIB ($(stat -c %s "$LIB") bytes, $EXPORTS T), debug symbols $DEBUG ($(stat -c %s "$DEBUG") bytes)"
done
