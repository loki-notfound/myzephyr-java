#!/usr/bin/env bash
#
# Writes dist/<version>/manifest.json and dist/<version>/SHA256SUMS for a release built by
# bin/build_release.sh. The manifest (JSON, sorted keys, python3 standard library only) records:
#   zephyr_java  version, commit sha of this checkout, SOURCE_DATE_EPOCH
#   core         both pins of bin/pins.env: tag (ZEPHYR_TAG, ZEPHYR_CPP_TAG), sha, URL; release_set
#                (RELEASE_SET, the zephyr-backports release set the pins belong to) and
#                release_set_members (RELEASE_SET_MEMBERS: name, tag, sha of every member; the
#                zephyr and zephyr-cpp members must equal the pins)
#   patches      every build-layer patch under patches/depends/: file name and sha256
#   toolchain    gcc, aarch64-linux-gnu-g++, binutils, glibc, cmake, JDK ($JAVA_HOME) and Maven
#                of the environment this script runs in, the packaging environment: the pinned
#                image for a release (packaging_env "container", packaging_container the image
#                from ZEPHYR_JAVA_CONTAINER, which bin/package_release_container.sh and the CI
#                package job set), else packaging_env "host" and packaging_container null;
#                Gradle (wrapper distribution and sha256), AGP, compileSdk, minSdk, build-tools;
#                android_sdk_packages: Pkg.Revision of cmdline-tools/latest,
#                platforms/android-<compileSdk> and build-tools/<build-tools> in $ANDROID_HOME
#                (null without one); android_natives: per Android native the build environment
#                that bin/android_build_env.sh recorded where it was built
#                (build/debug/android-<abi>/build-env: container image or none, Debian snapshot,
#                build path, triplet, API level, NDK, its sha256, clang); android_ndk (version and
#                sha256) and ndk_clang from those files (the value when all agree, else the list;
#                null without Android natives); linux_natives: per Linux native the build
#                environment that bin/build_linux.sh recorded (build/natives/linux-<arch>/build-env:
#                container image or none, glibc, compiler). The platforms are those of
#                BUILD_LINUX_ARCHES and BUILD_ANDROID_ABIS (defaults as in bin/package_release.sh);
#                a missing build-env file of one of them is an error, so the
#                CI package job and a local build record the same facts, from the natives'
#                builds, not from the packaging host; linux_container: that image when every Linux native
#                was built in the same one, null when all were built on the host, else the list;
#                debian_snapshot: the Debian snapshot the container builds install their packages
#                from (DEBIAN_SNAPSHOT in bin/container-build-deps.sh)
#   depends      every package of zephyr's contrib/depends/packages/*.mk: version, file name
#                and sha256 as declared there
#   previous_release
#                the release whose Maven repository bin/merge_previous_release.sh merged into the
#                site: version and site_archive_sha256 from bin/previous-release.env (null
#                without that file, a first release)
#   artifacts    every file in dist/<version>/ except manifest.json and SHA256SUMS: name, size,
#                sha256; for the site archive site-<version>.tar.gz also its entry count
#                The site directory (dist/<version>/site/, with the Maven repository in
#                site/maven/) is not listed: the archive holds it (bin/package_release.sh builds
#                both before the manifest).
# SHA256SUMS is sha256sum over the top-level files of dist/<version>/, the release assets: the
# manifest and the site archive included, nothing below site/ (so sha256sum -c works on the
# downloaded assets), one line per file.
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
    # without terminal colour codes (Debian's mvn writes them even with -B)
    out = re.sub(r"\x1b\[[0-9;]*m", "", out)
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
            value = m.group(2)
            if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
                value = value[1:-1]
            out[m.group(1)] = value
    return out


def release_set_members(pins):
    """RELEASE_SET_MEMBERS of bin/pins.env as a list; its zephyr and zephyr-cpp entries must be
    the pins themselves."""
    members = []
    for item in pins["RELEASE_SET_MEMBERS"].split():
        name, tag, sha = item.split(":")
        members.append({"name": name, "tag": tag, "sha": sha})
    by_name = {m["name"]: m for m in members}
    for name, tag_key, sha_key in (("zephyr", "ZEPHYR_TAG", "ZEPHYR_SHA"),
                                   ("zephyr-cpp", "ZEPHYR_CPP_TAG", "ZEPHYR_CPP_SHA")):
        m = by_name.get(name)
        if m is None or m["tag"] != pins[tag_key] or m["sha"] != pins[sha_key]:
            sys.exit(f"bin/pins.env: RELEASE_SET_MEMBERS {name} {m} differs from "
                     f"{tag_key}={pins[tag_key]} {sha_key}={pins[sha_key]}")
    return members


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


def platforms(var, default):
    """The platforms of this build: BUILD_LINUX_ARCHES or BUILD_ANDROID_ABIS (empty: none)."""
    value = os.environ.get(var)
    return (default if value is None else value).split()


def build_env(path):
    """KEY=VALUE lines of a build-env file; a missing file is an error, so CI and a local build
    record the same facts."""
    full = os.path.join(ROOT, path)
    if not os.path.isfile(full):
        sys.exit(f"no {path}: the build environment of a packaged native is missing")
    with open(full, encoding="utf-8") as f:
        return dict(l.rstrip("\n").split("=", 1) for l in f if "=" in l)


def linux_natives():
    return {f"linux-{a}": build_env(f"build/natives/linux-{a}/build-env")
            for a in platforms("BUILD_LINUX_ARCHES", "x86_64 arm64")}


def android_natives():
    return {f"android-{a}": build_env(f"build/debug/android-{a}/build-env")
            for a in platforms("BUILD_ANDROID_ABIS", "arm64-v8a armeabi-v7a x86_64")}


def one_or_list(values):
    """The value when every native agrees, else the sorted list; None without natives."""
    values = sorted(set(values))
    if not values:
        return None
    return values[0] if len(values) == 1 else values


def toolchain():
    natives = linux_natives()
    androids = android_natives()
    images = sorted({v.get("container", "none") for v in natives.values()})
    if not images or images == ["none"]:
        container = None
    elif len(images) == 1:
        container = images[0]
    else:
        container = images
    wrapper = shell_vars("android/gradle/wrapper/gradle-wrapper.properties")
    gradle_kts = read("android/build.gradle.kts")

    def kts(pattern):
        m = re.search(pattern, gradle_kts)
        return m.group(1) if m else None

    m = re.search(r"^DEBIAN_SNAPSHOT=(\S+)$", read("bin/container-build-deps.sh"), re.M)
    debian_snapshot = m.group(1) if m else None
    java_home = os.environ.get("JAVA_HOME", "")
    javac = os.path.join(java_home, "bin/javac") if java_home else "javac"
    packaging_container = os.environ.get("ZEPHYR_JAVA_CONTAINER") or None
    compile_sdk = kts(r"compileSdk = (\d+)")
    build_tools = kts(r'buildToolsVersion = "([^"]+)"')
    android_home = os.environ.get("ANDROID_HOME")

    def sdk_revision(rel):
        try:
            props = shell_vars(os.path.join(android_home, rel, "source.properties"))
        except OSError:
            return None
        return props.get("Pkg.Revision")

    sdk_packages = None
    if android_home:
        sdk_packages = {
            "cmdline-tools": sdk_revision("cmdline-tools/latest"),
            f"platforms;android-{compile_sdk}": sdk_revision(f"platforms/android-{compile_sdk}"),
            f"build-tools;{build_tools}": sdk_revision(f"build-tools/{build_tools}"),
        }
    return {
        "packaging_env": "container" if packaging_container else "host",
        "packaging_container": packaging_container,
        "android_sdk_packages": sdk_packages,
        "gcc": first_line("gcc", "--version"),
        "aarch64-linux-gnu-g++": first_line("aarch64-linux-gnu-g++", "--version"),
        "binutils": first_line("objcopy", "--version"),
        "glibc": first_line("ldd", "--version"),
        "linux_container": container,
        "debian_snapshot": debian_snapshot,
        "linux_natives": natives,
        "aarch64-linux-gnu-binutils": first_line("aarch64-linux-gnu-objcopy", "--version"),
        "android_ndk": ({"version": one_or_list(v["ndk"] for v in androids.values()),
                         "sha256": one_or_list(v["ndk_sha256"] for v in androids.values())}
                        if androids else None),
        "ndk_clang": one_or_list(v["clang"] for v in androids.values()),
        "android_natives": androids,
        "cmake": first_line("cmake", "--version"),
        "jdk": first_line(javac, "-version"),
        "maven": first_line("mvn", "-B", "--version"),
        "gradle": {"distribution": wrapper.get("distributionUrl", "").replace("\\:", ":"),
                   "sha256": wrapper.get("distributionSha256Sum")},
        "android_gradle_plugin": kts(r'id\("com\.android\.library"\) version "([^"]+)"'),
        "android_compile_sdk": compile_sdk,
        "android_min_sdk": kts(r"minSdk = (\d+)"),
        "android_build_tools": build_tools,
    }


def main():
    version = pom_version()
    dist = sys.argv[1] or os.path.join("dist", version)
    dist = os.path.join(ROOT, dist)
    if not os.path.isdir(dist):
        sys.exit(f"no {dist}: run bin/build_release.sh first")
    pins = shell_vars("bin/pins.env")
    previous_release = None
    if os.path.isfile(os.path.join(ROOT, "bin/previous-release.env")):
        prev = shell_vars("bin/previous-release.env")
        previous_release = {"version": prev["PREV_VERSION"],
                            "site_archive_sha256": prev["PREV_SITE_SHA256"]}
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
            "zephyr": {"tag": pins["ZEPHYR_TAG"], "sha": pins["ZEPHYR_SHA"],
                       "url": pins["ZEPHYR_URL"]},
            "zephyr-cpp": {"tag": pins["ZEPHYR_CPP_TAG"], "sha": pins["ZEPHYR_CPP_SHA"],
                           "url": pins["ZEPHYR_CPP_URL"]},
            "release_set": pins["RELEASE_SET"],
            "release_set_members": release_set_members(pins),
        },
        "patches": [{"name": n, "sha256": sha256(os.path.join(patches_dir, n))}
                    for n in sorted(os.listdir(patches_dir)) if n.endswith(".patch")],
        "toolchain": toolchain(),
        "previous_release": previous_release,
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
