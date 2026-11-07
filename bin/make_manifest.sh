#!/usr/bin/env bash
#
# Writes dist/<version>/manifest.json and dist/<version>/SHA256SUMS for a release built by
# bin/build_release.sh. The manifest (JSON, sorted keys, python3 standard library only) records:
#   zephyr_java  version, commit sha of this checkout, SOURCE_DATE_EPOCH
#   core         both pins of bin/pins.env: tag, sha, URL
#   patches      every build-layer patch under patches/depends/: file name and sha256
#   toolchain    gcc, aarch64-linux-gnu-g++, binutils, glibc of this host, NDK (version and
#                sha256 from android_ndk.mk), the NDK standalone toolchain's clang, cmake, JDK,
#                Maven, Gradle (wrapper distribution and sha256), AGP, compileSdk, minSdk,
#                build-tools; linux_natives: per Linux native the build environment that
#                bin/build_linux.sh recorded (build/natives/linux-<arch>/build-env: container image
#                or none, glibc, compiler); linux_container: that image when every Linux native
#                was built in the same one, null when all were built on the host, else the list;
#                debian_snapshot: the Debian snapshot the container builds install their packages
#                from (DEBIAN_SNAPSHOT in bin/container-build-deps.sh)
#   depends      every package of zephyr's contrib/depends/packages/*.mk: version, file name
#                and sha256 as declared there
#   artifacts    every file in dist/<version>/ except manifest.json and SHA256SUMS: name, size,
#                sha256; for the site archive site-<version>.tar.gz also its entry count
#                The site directory (dist/<version>/site/, with the Maven repository in
#                site/maven/) is not listed: the archive holds it (bin/package_release.sh builds
#                both before the manifest).
# SHA256SUMS is sha256sum over the top-level files of dist/<version>/, the release assets: the
# manifest and the site archive included, nothing below site/ (QUESTIONS R13-4: sha256sum -c
# works on the downloaded assets), one line per file.
# With --sums-only, only SHA256SUMS is rewritten (bin/package_release.sh does that last).
# A tool that is not installed is recorded as null.
#
# Usage: bin/make_manifest.sh [--sums-only] [dist/<version>]   (default: dist/<version of pom.xml>)

set -euo pipefail

cd "$(dirname "$0")/.."

if [ -z "${SOURCE_DATE_EPOCH:-}" ]; then
  SOURCE_DATE_EPOCH="$(git log -1 --format=%ct HEAD)"
fi
export SOURCE_DATE_EPOCH

SUMS_ONLY=0
if [ "${1:-}" = --sums-only ]; then
  SUMS_ONLY=1
  shift
fi

[ "$SUMS_ONLY" = 1 ] || python3 -I - "${1:-}" <<'PY'
import hashlib
import json
import os
import re
import subprocess
import sys
import tarfile
import xml.etree.ElementTree as ET

ROOT = os.getcwd()
DEPENDS = os.path.join(ROOT, "external/zephyr-cpp/external/zephyr/contrib/depends")


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for block in iter(lambda: f.read(1 << 20), b""):
            h.update(block)
    return h.hexdigest()


def first_line(*cmd):
    """First non-empty output line of a command (stdout and stderr), or None if it fails."""
    try:
        out = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                             text=True, check=True).stdout
    except (OSError, subprocess.CalledProcessError):
        return None
    lines = [l.strip() for l in out.splitlines() if l.strip()]
    return lines[0] if lines else None


def read(path):
    with open(os.path.join(ROOT, path), encoding="utf-8") as f:
        return f.read()


def pom_version():
    ns = {"m": "http://maven.apache.org/POM/4.0.0"}
    return ET.parse(os.path.join(ROOT, "pom.xml")).getroot().find("m:version", ns).text.strip()


def shell_vars(path):
    """KEY=VALUE lines of a sourced shell file (bin/pins.env, gradle.properties)."""
    out = {}
    for line in read(path).splitlines():
        m = re.match(r"^([A-Za-z_][A-Za-z0-9_.]*)=(.*)$", line.strip())
        if m:
            out[m.group(1)] = m.group(2)
    return out


def mk_vars(path):
    """$(package)_<name>=value declarations of a depends package file."""
    out = {}
    with open(path, encoding="utf-8") as f:
        for line in f:
            m = re.match(r"^\$\(package\)_([a-z0-9_]+)=(.*)$", line.rstrip("\n"))
            if m and m.group(1) not in out:
                out[m.group(1)] = m.group(2).strip()
    return out


def depends_packages():
    pkgdir = os.path.join(DEPENDS, "packages")
    raw = {}
    for name in sorted(os.listdir(pkgdir)):
        if name.endswith(".mk") and name != "packages.mk":
            raw[name[:-3]] = mk_vars(os.path.join(pkgdir, name))

    def resolve(pkg, value, depth=0):
        # $($(package)_x) refers to the same package, $(native_$(package)_x) to native_<package>
        def sub(m):
            other, key = m.group(1), m.group(2)
            target = (other + pkg) if other else pkg
            return resolve(target, raw.get(target, {}).get(key, m.group(0)), depth + 1)
        if depth > 5:
            return value
        value = re.sub(r"\$\((?:\$\(package\)|([a-z_]*)\$\(package\))_([a-z0-9_]+)\)", sub, value)
        return value.replace("$(package)", pkg)

    out = {}
    for pkg, v in raw.items():
        out[pkg] = {k: resolve(pkg, v.get(k)) if v.get(k) is not None else None
                    for k in ("version", "file_name", "sha256_hash")}
    return out


def linux_natives():
    out = {}
    base = os.path.join(ROOT, "build/natives")
    if os.path.isdir(base):
        for d in sorted(os.listdir(base)):
            env = os.path.join(base, d, "build-env")
            if d.startswith("linux-") and os.path.isfile(env):
                with open(env, encoding="utf-8") as f:
                    out[d] = dict(l.rstrip("\n").split("=", 1) for l in f if "=" in l)
    return out


def toolchain():
    natives = linux_natives()
    images = sorted({v.get("container", "none") for v in natives.values()})
    if not images or images == ["none"]:
        container = None
    elif len(images) == 1:
        container = images[0]
    else:
        container = images
    ndk = mk_vars(os.path.join(DEPENDS, "packages/android_ndk.mk"))
    wrapper = shell_vars("android/gradle/wrapper/gradle-wrapper.properties")
    gradle_kts = read("android/build.gradle.kts")

    def kts(pattern):
        m = re.search(pattern, gradle_kts)
        return m.group(1) if m else None

    clang = None
    for triplet in ("aarch64-linux-android", "arm-linux-androideabi", "x86_64-linux-android"):
        c = os.path.join(DEPENDS, triplet, "native/bin/clang")
        if os.path.exists(c):
            clang = first_line(c, "--version")
            break
    m = re.search(r"^DEBIAN_SNAPSHOT=(\S+)$", read("bin/container-build-deps.sh"), re.M)
    debian_snapshot = m.group(1) if m else None
    java_home = os.environ.get("JAVA_HOME", "")
    javac = os.path.join(java_home, "bin/javac") if java_home else "javac"
    return {
        "gcc": first_line("gcc", "--version"),
        "aarch64-linux-gnu-g++": first_line("aarch64-linux-gnu-g++", "--version"),
        "binutils": first_line("objcopy", "--version"),
        "glibc": first_line("ldd", "--version"),
        "linux_container": container,
        "debian_snapshot": debian_snapshot,
        "linux_natives": natives,
        "aarch64-linux-gnu-binutils": first_line("aarch64-linux-gnu-objcopy", "--version"),
        "android_ndk": {"version": "r" + ndk.get("version", "?"), "sha256": ndk.get("sha256_hash")},
        "ndk_clang": clang,
        "cmake": first_line("cmake", "--version"),
        "jdk": first_line(javac, "-version"),
        "maven": first_line("mvn", "-B", "--version"),
        "gradle": {"distribution": wrapper.get("distributionUrl", "").replace("\\:", ":"),
                   "sha256": wrapper.get("distributionSha256Sum")},
        "android_gradle_plugin": kts(r'id\("com\.android\.library"\) version "([^"]+)"'),
        "android_compile_sdk": kts(r"compileSdk = (\d+)"),
        "android_min_sdk": kts(r"minSdk = (\d+)"),
        "android_build_tools": kts(r'buildToolsVersion = "([^"]+)"'),
    }


def main():
    version = pom_version()
    dist = sys.argv[1] or os.path.join("dist", version)
    dist = os.path.join(ROOT, dist)
    if not os.path.isdir(dist):
        sys.exit(f"no {dist}: run bin/build_release.sh first")
    pins = shell_vars("bin/pins.env")
    patches_dir = os.path.join(ROOT, "patches/depends")
    artifacts = []
    for name in sorted(os.listdir(dist)):
        path = os.path.join(dist, name)
        if name in ("manifest.json", "SHA256SUMS") or not os.path.isfile(path):
            continue
        entry = {"name": name, "size": os.path.getsize(path), "sha256": sha256(path)}
        if name.startswith("site-") and name.endswith(".tar.gz"):
            with tarfile.open(path, "r:gz") as t:
                entry["entries"] = len(t.getmembers())
        artifacts.append(entry)
    manifest = {
        "zephyr_java": {
            "version": version,
            "commit": subprocess.run(["git", "rev-parse", "HEAD"], stdout=subprocess.PIPE,
                                     text=True, check=True).stdout.strip(),
            "source_date_epoch": int(os.environ["SOURCE_DATE_EPOCH"]),
        },
        "core": {
            "zephyr": {"tag": pins["PIN_TAG"], "sha": pins["ZEPHYR_SHA"], "url": pins["ZEPHYR_URL"]},
            "zephyr-cpp": {"tag": pins["PIN_TAG"], "sha": pins["ZEPHYR_CPP_SHA"],
                           "url": pins["ZEPHYR_CPP_URL"]},
        },
        "patches": [{"name": n, "sha256": sha256(os.path.join(patches_dir, n))}
                    for n in sorted(os.listdir(patches_dir)) if n.endswith(".patch")],
        "toolchain": toolchain(),
        "depends": depends_packages(),
        "artifacts": artifacts,
    }
    with open(os.path.join(dist, "manifest.json"), "w", encoding="utf-8") as f:
        json.dump(manifest, f, indent=2, sort_keys=True)
        f.write("\n")
    print(f"wrote {os.path.relpath(dist, ROOT)}/manifest.json: {len(artifacts)} artifacts, "
          f"{len(manifest['depends'])} depends packages, {len(manifest['patches'])} patches")


main()
PY

DIST="${1:-dist/$(python3 -I -c 'import sys,xml.etree.ElementTree as E; print(E.parse(sys.argv[1]).getroot().find("{http://maven.apache.org/POM/4.0.0}version").text.strip())' pom.xml)}"
(cd "$DIST" && find . -maxdepth 1 -type f ! -name SHA256SUMS -printf '%P\n' | LC_ALL=C sort | xargs -d '\n' sha256sum > SHA256SUMS)
echo "wrote $DIST/SHA256SUMS: $(wc -l < "$DIST/SHA256SUMS") files"
