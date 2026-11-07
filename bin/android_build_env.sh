#!/usr/bin/env bash
#
# Writes build/debug/android-<abi>/build-env, the build environment of one Android native for the
# manifest, read from the depends prefix and the NDK where the build ran (so CI's package job,
# which has no NDK, records the same facts as a local build):
#   container        the image (ZEPHYR_JAVA_CONTAINER, set by bin/build_android_container.sh and
#                    the CI natives job) or none
#   debian_snapshot  DEBIAN_SNAPSHOT of bin/container-build-deps.sh in a container, else none
#   build_path       the checkout's path (/work for releases: NDK r18b keeps __FILE__ paths)
#   triplet          the depends host triplet
#   api              ANDROID_API of contrib/depends/hosts/android.mk
#   ndk              the NDK version of contrib/depends/packages/android_ndk.mk (r18b)
#   ndk_sha256       its sha256 as declared there
#   clang            the first line of the standalone toolchain's clang --version
# The file sits next to the debug file, outside the AAR's jniLibs source directory (build/android/),
# and bin/package_release.sh leaves it out of the debug-symbols archive. bin/build_android.sh
# calls this script after each ABI; it can be run alone for natives built earlier, in the same
# environment (the container for releases).
#
# Usage: bin/android_build_env.sh <arm64-v8a|armeabi-v7a|x86_64>

set -euo pipefail

case "${1:-}" in
  arm64-v8a) TRIPLET=aarch64-linux-android ;;
  armeabi-v7a) TRIPLET=arm-linux-androideabi ;;
  x86_64) TRIPLET=x86_64-linux-android ;;
  *) echo "usage: $0 <arm64-v8a|armeabi-v7a|x86_64>" >&2; exit 2 ;;
esac
ABI="$1"

cd "$(dirname "$0")/.."
ROOT="$(pwd)"
DEPENDS=external/zephyr-cpp/external/zephyr/contrib/depends

CLANG="$DEPENDS/$TRIPLET/native/bin/clang"
[ -x "$CLANG" ] || { echo "no $CLANG: build the depends for $TRIPLET first (bin/build_android.sh)" >&2; exit 1; }
CLANG_VERSION="$("$CLANG" --version | sed -n '/./{p;q}')"
API="$(sed -n 's/^ANDROID_API=//p' "$DEPENDS/hosts/android.mk")"
NDK="$(sed -n 's/^\$(package)_version=//p' "$DEPENDS/packages/android_ndk.mk" | head -1)"
NDK_SHA256="$(sed -n 's/^\$(package)_sha256_hash=//p' "$DEPENDS/packages/android_ndk.mk" | head -1)"
for V in CLANG_VERSION API NDK NDK_SHA256; do
  [ -n "${!V}" ] || { echo "$ABI: could not read $V" >&2; exit 1; }
done

CONTAINER="${ZEPHYR_JAVA_CONTAINER:-none}"
SNAPSHOT=none
if [ "$CONTAINER" != none ]; then
  SNAPSHOT="$(sed -n 's/^DEBIAN_SNAPSHOT=//p' bin/container-build-deps.sh)"
fi

OUT="build/debug/android-$ABI/build-env"
mkdir -p "$(dirname "$OUT")"
{
  echo "container=$CONTAINER"
  echo "debian_snapshot=$SNAPSHOT"
  echo "build_path=$ROOT"
  echo "triplet=$TRIPLET"
  echo "api=$API"
  echo "ndk=r$NDK"
  echo "ndk_sha256=$NDK_SHA256"
  echo "clang=$CLANG_VERSION"
} > "$OUT"
echo "wrote $OUT ($(tr '\n' ' ' < "$OUT"))"
