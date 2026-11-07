#!/usr/bin/env bash
#
# Installs, in the debian:12 build image, the Debian 12 packages that bin/build_linux.sh needs:
# the toolchain for zephyr's contrib/depends (hosts x86_64-linux-gnu and aarch64-linux-gnu), cmake
# and ccache for zephyr and the JNI library, and a JDK for jni.h/jni_md.h only (the JNI headers;
# the cross build to arm64 uses the same headers, jni_md.h is per OS, not per architecture).
# Nothing for Android, nothing for Maven or Gradle.
# Runs as root inside the container (bin/build_linux_container.sh, the CI natives job); it is not
# meant for a developer host.
#
# Usage: bin/linux-build-deps.sh

set -euo pipefail

export DEBIAN_FRONTEND=noninteractive
apt-get update -q
apt-get install -y -q --no-install-recommends \
  build-essential cmake git curl ca-certificates pkg-config \
  autoconf automake libtool-bin gperf bison flex python3 ccache patch \
  xz-utils bzip2 file \
  g++-aarch64-linux-gnu \
  openjdk-17-jdk-headless
