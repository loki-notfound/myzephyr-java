#!/usr/bin/env bash
#
# Installs, in the pinned debian:12 build image, every Debian 12 package the native builds need,
# from one Debian snapshot, so CI and a later local build get the same toolchain packages
# the image digest pins only the base image, and apt's live mirror moves on.
#   1. apt's sources are replaced by snapshot.debian.org at DEBIAN_SNAPSHOT (below) for bookworm,
#      bookworm-updates and bookworm-security, over plain http: the base image has no
#      ca-certificates, so apt cannot use an https source before anything is installed, and
#      integrity does not depend on TLS here, because apt verifies the snapshot's Release files
#      (Debian's own signed files) with the image's debian-archive-keyring.
#      Acquire::Check-Valid-Until=false: the archived Release files have expired.
#      Acquire::Retries=5: snapshot.debian.org throttles at times (HTTP 429/503).
#      An index that cannot be fetched stops the script; nothing comes from the live mirror,
#      ca-certificates included.
#   2. the packages of
#      Linux (bin/build_linux.sh): the toolchain for zephyr's contrib/depends (hosts
#        x86_64-linux-gnu and aarch64-linux-gnu), cmake and ccache for zephyr and the JNI library;
#      Android (bin/build_android.sh): depends' host tools for the NDK r18b builds (gperf, bison,
#        flex, libtool-bin, autoconf, automake, unzip, ccache, python3, patch, curl, xz-utils,
#        file);
#      both: a JDK for jni.h/jni_md.h (the JNI headers; jni_md.h is per OS, not per
#        architecture) and git (the build scripts place the zephyr tree by sha);
#      packaging (bin/package_release_container.sh, the CI package job): the
#        same OpenJDK 17 runs javac, javadoc, Maven and Gradle, and maven (Debian 12: 3.8.7) builds
#        the JAR; Gradle comes through the wrapper (pinned distribution, sha256 checked) and the
#        Android SDK from outside the image (bin/package_release_container.sh mounts it, the CI
#        package job installs it from the pinned command-line tools; unzip is above).
# bin/make_manifest.sh records DEBIAN_SNAPSHOT as toolchain.debian_snapshot.
# Runs as root inside the container (bin/build_linux_container.sh, bin/build_android_container.sh,
# bin/package_release_container.sh, the CI natives and package jobs); it is not meant for a
# developer host.
#
# Usage: bin/container-build-deps.sh

set -euo pipefail

DEBIAN_SNAPSHOT=20261107T000000Z
SNAPSHOT_URL=http://snapshot.debian.org/archive

export DEBIAN_FRONTEND=noninteractive

rm -f /etc/apt/sources.list /etc/apt/sources.list.d/*.list /etc/apt/sources.list.d/*.sources
cat > /etc/apt/sources.list.d/debian-snapshot.sources <<SOURCES
Types: deb
URIs: $SNAPSHOT_URL/debian/$DEBIAN_SNAPSHOT/
Suites: bookworm bookworm-updates
Components: main
Signed-By: /usr/share/keyrings/debian-archive-keyring.gpg

Types: deb
URIs: $SNAPSHOT_URL/debian-security/$DEBIAN_SNAPSHOT/
Suites: bookworm-security
Components: main
Signed-By: /usr/share/keyrings/debian-archive-keyring.gpg
SOURCES
APT_OPTS=(-o Acquire::Check-Valid-Until=false -o Acquire::Retries=5)
echo "apt from the Debian snapshot $DEBIAN_SNAPSHOT"

# apt-get update exits 0 when an index fails to download: check its warnings as well
LOG="$(mktemp)"
apt-get "${APT_OPTS[@]}" update -q 2>&1 | tee "$LOG"
if grep -qE '^(W: Failed to fetch|W: Some index files failed|E: )' "$LOG"; then
  echo "apt-get update from the Debian snapshot $DEBIAN_SNAPSHOT failed" >&2
  exit 1
fi
rm -f "$LOG"

apt-get "${APT_OPTS[@]}" install -y -q --no-install-recommends \
  build-essential cmake git curl ca-certificates pkg-config \
  autoconf automake libtool-bin gperf bison flex python3 ccache patch \
  xz-utils bzip2 unzip file \
  g++-aarch64-linux-gnu \
  openjdk-17-jdk-headless maven
