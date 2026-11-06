#!/usr/bin/env bash
#
# Builds libzephyr-java from pinned sources.
#
# 1. Checks that the zephyr-cpp submodule is at the pinned sha (bin/pins.env).
# 2. Places the zephyr tree at external/zephyr-cpp/external/zephyr, fetched by sha from the
#    zephyr-backports fork. zephyr-cpp's own bin/update_submodules.sh is never used: it checks
#    out master. zephyr-cpp declares external/zephyr in .gitmodules without a gitlink, so the
#    tree is not committed anywhere; it is excluded in the submodule's git directory
#    (info/exclude, inside .git, not in the tree) so the submodule stays clean.
# 3. Builds zephyr in external/zephyr-cpp/external/zephyr/build/release with the settings of
#    zephyr's "make release-static" target plus ccache. Skipped when libwallet.a already exists
#    and the tree is at the pinned sha; to rebuild, remove that build directory first.
# 4. Builds the JNI library in build/ (CMakeLists.txt of this repo).
#
# Usage: bin/build_libzephyr_java.sh [--keep-going]
#   --keep-going  pass -k to the JNI build so every compile error is collected
#                 (the zephyr build never uses -k)

set -euo pipefail

KEEP_GOING=0
if [ "${1:-}" = "--keep-going" ]; then
  KEEP_GOING=1
fi

cd "$(dirname "$0")/.."
ROOT="$(pwd)"
# shellcheck source=pins.env
. bin/pins.env

# 1. zephyr-cpp pin
CPP_HEAD="$(git -C external/zephyr-cpp rev-parse HEAD 2>/dev/null || true)"
if [ "$CPP_HEAD" != "$ZEPHYR_CPP_SHA" ]; then
  echo "external/zephyr-cpp is at '${CPP_HEAD}', expected ${ZEPHYR_CPP_SHA}." >&2
  echo "Initialise the submodule first: git submodule update --init" >&2
  exit 1
fi

# 2. zephyr tree, pinned by sha
Z=external/zephyr-cpp/external/zephyr
if [ ! -d "$Z/.git" ]; then
  git init -q "$Z" && git -C "$Z" remote add origin "$ZEPHYR_URL"
fi
if [ "$(git -C "$Z" rev-parse HEAD 2>/dev/null)" != "$ZEPHYR_SHA" ]; then
  git -C "$Z" fetch -q --depth 1 origin "$ZEPHYR_SHA" && git -C "$Z" checkout -q FETCH_HEAD
fi
git -C "$Z" submodule update -q --init --recursive --depth 1
[ "$(git -C "$Z" rev-parse HEAD)" = "$ZEPHYR_SHA" ] || { echo "zephyr tree is not at $ZEPHYR_SHA"; exit 1; }

# Exclude the zephyr tree in the submodule's git directory (.git/info/exclude of zephyr-cpp;
# for a submodule that directory is under the superproject's .git/modules/).
EXCLUDE="$(git -C external/zephyr-cpp rev-parse --path-format=absolute --git-path info/exclude)"
mkdir -p "$(dirname "$EXCLUDE")"
grep -qxF 'external/zephyr' "$EXCLUDE" 2>/dev/null || echo 'external/zephyr' >> "$EXCLUDE"

# 3. zephyr, release-static settings plus ccache
if [ -f "$Z/build/release/lib/libwallet.a" ]; then
  echo "zephyr: $Z/build/release/lib/libwallet.a exists at $ZEPHYR_SHA, skipping the zephyr build"
else
  mkdir -p "$Z/build/release" && cd "$Z/build/release"
  cmake -D STATIC=ON -D BUILD_64=ON -D CMAKE_BUILD_TYPE=Release -D USE_CCACHE=ON ../..
  make -j"$(nproc)"
  cd "$ROOT"
fi

# 4. JNI library
mkdir -p build && cd build
cmake ..
if [ "$KEEP_GOING" = 1 ]; then
  cmake --build . -j"$(nproc)" -- -k
else
  cmake --build . -j"$(nproc)"
fi
