#!/usr/bin/env bash
#
# Builds libzephyr-java from pinned sources.
#
# This is the quick developer build: zephyr and the JNI library link against the host's shared
# system libraries (boost, OpenSSL, unbound, sodium, hidapi, protobuf), so the result runs only
# on hosts like this one. Releases use bin/build_linux.sh, which builds the Linux natives through
# zephyr's contrib/depends and needs only the C runtime of the host.
#
# 1. Checks that the zephyr-cpp submodule is at the pinned sha (bin/pins.env).
#    Both pinned trees: when the pin tag (PIN_TAG) is present locally, it must point at the pin
#    and git verify-tag must pass; without gpg or the release key that is only a warning.
# 2. Places the zephyr tree at external/zephyr-cpp/external/zephyr, fetched by sha from the
#    zephyr-backports fork. zephyr-cpp's own bin/update_submodules.sh is never used: it checks
#    out master. zephyr-cpp declares external/zephyr in .gitmodules without a gitlink, so the
#    tree is not committed anywhere; it is excluded in the submodule's git directory
#    (info/exclude, inside .git, not in the tree) so the submodule stays clean.
# 3. Builds zephyr in external/zephyr-cpp/external/zephyr/build/release with the settings of
#    zephyr's "make release-static" target plus ccache, library targets only. cmake is skipped
#    when the build directory has a CMakeCache.txt; make always runs (a no-op when up to date).
# 4. Builds the JNI library in build/ (CMakeLists.txt of this repo).
# 5. Copies it to target/natives/linux-x86_64/, which Maven packages into the JAR.
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

# 1. zephyr-cpp pin
CPP_HEAD="$(git -C external/zephyr-cpp rev-parse HEAD 2>/dev/null || true)"
if [ "$CPP_HEAD" != "$ZEPHYR_CPP_SHA" ]; then
  echo "external/zephyr-cpp is at '${CPP_HEAD}', expected ${ZEPHYR_CPP_SHA}." >&2
  echo "Initialise the submodule first: git submodule update --init" >&2
  exit 1
fi
verify_pin_tag external/zephyr-cpp "$ZEPHYR_CPP_SHA"

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
verify_pin_tag "$Z" "$ZEPHYR_SHA"

# Exclude the zephyr tree in the submodule's git directory (.git/info/exclude of zephyr-cpp;
# for a submodule that directory is under the superproject's .git/modules/).
EXCLUDE="$(git -C external/zephyr-cpp rev-parse --path-format=absolute --git-path info/exclude)"
mkdir -p "$(dirname "$EXCLUDE")"
grep -qxF 'external/zephyr' "$EXCLUDE" 2>/dev/null || echo 'external/zephyr' >> "$EXCLUDE"

# 3. zephyr, release-static settings plus ccache
mkdir -p "$Z/build/release" && cd "$Z/build/release"
if [ -f CMakeCache.txt ]; then
  echo "zephyr: $Z/build/release/CMakeCache.txt exists, skipping cmake"
else
  cmake -D STATIC=ON -D BUILD_64=ON -D CMAKE_BUILD_TYPE=Release -D USE_CCACHE=ON ../..
fi
# Only the wallet targets: the daemon, the tests and miniupnpc's sample are not needed,
# and the sample does not compile with gcc 14 (strdup undeclared under -std=c11).
# The wallet-rpc executable is built in a later round.
# blocks is not built by this tree (PER_BLOCK_CHECKPOINT 0) and not needed by the wallet.
make -j"$(nproc)" wallet cryptonote_protocol
cd "$ROOT"

# Every static library zephyr-cpp imports from the zephyr build must exist (libwallet-crypto.a
# is optional there, guarded by if (EXISTS ...), and is skipped here; so is libblocks.a, see above).
MISSING=0
for LIB in $(grep -o '\${MONERO_PROJECT_BUILD}/[^)[:space:]]*\.a' external/zephyr-cpp/CMakeLists.txt \
             | grep -v -e '/libwallet-crypto\.a$' -e '/libblocks\.a$' | sort -u); do
  P="$Z/build/release/${LIB#\$\{MONERO_PROJECT_BUILD\}/}"
  if [ ! -f "$P" ]; then
    echo "missing zephyr library: $P" >&2
    MISSING=1
  fi
done
[ "$MISSING" = 0 ] || exit 1

# 4. JNI library
mkdir -p build && cd build
cmake ..
if [ "$KEEP_GOING" = 1 ]; then
  cmake --build . -j"$(nproc)" -- -k
else
  cmake --build . -j"$(nproc)"
fi
cd "$ROOT"

# 5. the library as a JAR resource: Maven packages target/natives/<os>-<arch>/ (pom.xml), and
# ZephyrUtils.loadNativeLibrary() loads it from there when it is not on java.library.path.
# target/ is Maven's output, so a clean build needs this step again before the package.
mkdir -p target/natives/linux-x86_64
cp build/libzephyr-java.so target/natives/linux-x86_64/libzephyr-java.so
echo "copied build/libzephyr-java.so to target/natives/linux-x86_64/"
