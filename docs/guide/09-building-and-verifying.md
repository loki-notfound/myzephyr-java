# 9. Building from source and verifying releases

Every zephyr-java release is built from pinned sources with scripts in this repository, and every
release can be rebuilt and compared. This chapter describes the pins, the build-layer patches,
the builds, the manifest, the signatures and how to reproduce a release.

## The pins

`bin/pins.env` names the two source trees of the native core, by commit:

| Tree | Repository | Tag | Commit |
|---|---|---|---|
| zephyr | `zephyr-backports/zephyr` | `v2.3.0-sec.1` | `e9cf10f796a6053280110dbe453dcd4e4344c8e8` |
| zephyr-cpp | `zephyr-backports/zephyr-cpp` | `v2.3.0-sec.1` | `3a2ee602ee28e6b7941b617ad4706349ad992641` |

zephyr-cpp is the git submodule `external/zephyr-cpp`, at that commit. The zephyr tree is placed
by the build scripts at `external/zephyr-cpp/external/zephyr`, fetched by commit; zephyr-cpp's
own `bin/update_submodules.sh` is never used, because it checks out a branch. Nothing is ever
built from a branch. When the pin tag is present locally, the scripts check that it points at the
pinned commit and run `git verify-tag` on it (see "Signatures" below).
<!-- bin/pins.env; bin/build_libzephyr_java.sh header steps 1-2 and verify_pin_tag; .gitmodules -->

The backports are ZephyrProtocol's Zephyr 2.3.0 plus Monero security fixes, with no changes to the
headers that zephyr-java compiles against. A build against ZephyrProtocol's original release must
therefore work too: zephyr tag `v2.3.0` (`67c5f53b878fef41fb5e74c4382d5b7a2f37fd8a`) and
zephyr-cpp `4a99272f6d7a171cf6625016939094637459975a` (zephyr-cpp has no tag there). The comments
in `bin/pins.env` name both; to build against them, put their URLs and commits into the four
variables and check out the submodule at that commit. This is a requirement on the sources, not a
CI job; releases are always built from the backports.
<!-- PLAN §2 "Source pins" (paper requirement, no CI matrix); ZEPHYR-DELTA design goal 3 (no header changes between 4a99272 and 56abaf9); bin/pins.env comments (ZEPHYR_CPP_URL, ZEPHYR_CPP_SHA, ZEPHYR_URL, ZEPHYR_SHA) -->

## Build-layer patches

zephyr-java never patches zephyr or zephyr-cpp source code. It does patch build tooling: five
patches under `patches/depends/`, each with a DEP-3 header, which the build scripts apply to
zephyr's `contrib/depends` (the tree that builds boost, OpenSSL, unbound and the other
libraries from pinned archives):

| Patch | What | Why |
|---|---|---|
| `0001-android-ndk-python3` | run NDK r18b's toolchain script with `python3` | current hosts have no Python 2 |
| `0002-boost-1.64-b2-engine-declarations` | add the includes and two prototypes boost 1.64's build engine needs | gcc 14 rejects implicit declarations |
| `0003-android-ndk-x86_64` | pass `--arch x86_64` for the x86_64 standalone toolchain | the x86_64 Android toolchain is not built otherwise |
| `0004-openssl-openssldir` | build OpenSSL with `OPENSSLDIR /etc/ssl` | the default embedded the build directory, an empty CA store on any other machine |
| `0005-neutral-engines-and-unbound-sysconfdir` | neutral `ENGINESDIR`, `MODULESDIR` and unbound `sysconfdir` | they embedded the build directory, which breaks reproducibility across paths |
<!-- patches/depends/000[1-5]*.patch Description lines; register D21; PLAN §5 R9 result (three patches) and R11 result (0004, 0005); QUESTIONS R11-2 answer (0005 values; unused with no-dso); evidence/r10/packaging-r10.md stage E (OPENSSLDIR embedded the build path) -->

Two more build-layer settings: the JNI library is compiled with `-include map`, because
zephyr-cpp's model header relies on boost to include `<map>` and newer boost does not (register
D20); and zephyr's dead `blocks` library is not linked, because the pinned tree never builds it
(D22).
<!-- CMakeLists.txt:369 (target_compile_options -include map); register D20, D22 -->

## The builds

Clone and fetch the submodule (not recursively; the scripts place the zephyr tree themselves):

```sh
git clone https://github.com/loki-notfound/zephyr-java.git
cd zephyr-java
git submodule update --init external/zephyr-cpp
```

There are four entry points:

- **`bin/build_libzephyr_java.sh`**, the developer build. It builds zephyr and the JNI library
  against the host's shared libraries (boost, OpenSSL, unbound, sodium, hidapi, protobuf) into
  `build/`, where `mvn test` finds it. Quick, but the result runs only on hosts like the one that
  built it. The CI workflow's "System libraries of the developer build" step lists the packages it
  needs on Ubuntu 24.04.
- **`bin/build_linux_container.sh <x86_64|arm64|--all>`**, the Linux release natives. It runs
  `bin/build_linux.sh` in the pinned `debian:12` image with podman or docker (the same image as the
  CI job), so everything but glibc comes from zephyr's depends and is linked statically. The
  results are `build/natives/linux-<arch>/libzephyr-java.so`.
- **`bin/build_android.sh <abi|--all>`**, the Android natives with zephyr's depends and NDK r18b
  (downloaded and checked by depends), into `build/android/<abi>/`. For releases,
  **`bin/build_android_container.sh <abi|--all>`** runs it in the same pinned image.
- **`bin/build_release.sh`**, everything: the natives, stripping with separate debug files, the JAR
  with sources and Javadoc jars (the pure tests run on the depends-built library), the AAR through
  Gradle in `android/`, and `dist/<version>/` with `manifest.json` and `SHA256SUMS`. It needs a JDK
  21 in `JAVA_HOME` and an Android SDK with platform 36 and build-tools 36.0.0 in `ANDROID_HOME`.
  `BUILD_LINUX_ARCHES` and `BUILD_ANDROID_ABIS` choose the platforms; an empty value builds none.
<!-- bin/build_libzephyr_java.sh, bin/build_linux.sh, bin/build_linux_container.sh, bin/build_android.sh, bin/build_android_container.sh, bin/build_release.sh header comments; .github/workflows/build.yml (java job "System libraries of the developer build"; natives job container debian:12@sha256:366a6da5...) -->

Every step is a plain shell command, so the same scripts serve a developer, CI and an F-Droid
build. The first depends build takes a while: about 14 minutes per Android ABI and 10 minutes
for Linux x86_64 on the machine this project is developed on.
<!-- bin/build_release.sh header ("F-Droid runs this script; CI runs the same scripts"); PLAN §5 R9 result (about 14 minutes of depends per ABI); QUESTIONS R11-13 (13 depends packages in 9 m 55 s in the container) -->

## Reproducibility

Two builds from the same commit give the same files:

- `SOURCE_DATE_EPOCH` is the commit time unless set, and goes into every step; Maven gets it as
  `project.build.outputTimestamp`.
- The Linux natives are built with gcc and `-ffile-prefix-map`, so they contain no build path and
  are the same from **any** checkout directory. Verified: the x86_64 library and its debug file
  were equal across two checkout paths.
- The Android natives are reproducible at the **same** build path only: NDK r18b's clang 7 cannot
  rewrite `__FILE__`, so about 700 source paths per library keep the build directory. Release
  builds therefore run at a fixed path, as F-Droid does. Verified: `arm64-v8a` was equal on a
  rebuild at the same path.
- The JARs and the AAR were equal across clean builds. Stripping is deterministic, and the debug
  symbols are packed into a tar with fixed order, times and owners.
<!-- bin/build_release.sh (SOURCE_DATE_EPOCH, OUTPUT_TIMESTAMP, tar --sort=name --mtime --owner=0); RELEASES §4 (refined 2026-11-07, R11); PLAN §5 R11 result ("Linux x86_64 library and .debug equal across two checkout paths; arm64-v8a equal on a same-path rebuild; jars and AAR equal across clean builds") -->

## The manifest

`dist/<version>/manifest.json` records what went into a release:

| Key | Contents |
|---|---|
| `core` | zephyr and zephyr-cpp: repository URL, tag, commit |
| `depends` | every package of zephyr's depends: archive name, version, sha256 |
| `patches` | the build-layer patches: name, sha256 |
| `toolchain` | gcc, binutils, CMake, glibc, JDK, Maven, NDK (version and sha256) and its clang, Android Gradle plugin, Gradle (distribution and sha256), compile and min SDK, build-tools; per Linux native its compiler, glibc and container |
| `zephyr_java` | the commit, its `SOURCE_DATE_EPOCH`, the version |
| `artifacts` | every release file with its size and sha256 |
<!-- bin/make_manifest.sh header; dist/2.3.0-sec.1-java.1-SNAPSHOT/manifest.json of the R11 run (keys core, depends, patches, toolchain, zephyr_java, artifacts) -->

`SHA256SUMS` lists every file of the release, the manifest included. The Maven repository and the
site are the archive `site-<version>.tar.gz`, whose hash is in `SHA256SUMS`; Pages serves its
contents.
<!-- bin/make_manifest.sh header ("SHA256SUMS is sha256sum over the top-level files"); bin/package_release.sh step 7 (the site archive) -->

## Signatures

Two keys are involved:

- **The maintainer key** `31DA 39AB 43E0 CA67 F1F3  A661 BA2A FF22 DF8A 4A13` signs the zephyr-java
  release tags and `SHA256SUMS`. It is [`KEYS.asc`](../../KEYS.asc) in this repository.
- **The zephyr-backports release key** `FB39 94F0 997C A6F0 43D7  2491 9EFF 03F5 27D2 40A1` signs the
  core's tags. The build scripts verify them with `git verify-tag` when the tag is present; without
  gpg or without the key they print a warning and build anyway, and a bad signature stops the build.
<!-- PLAN §7.8; RELEASES §3; bin/pins.env comment; verify_pin_tag in the build scripts -->

To check a release you downloaded:

```sh
gpg --import KEYS.asc
git verify-tag v2.3.0-sec.1-java.1                      # in a clone of this repository
gpg --verify SHA256SUMS.asc && sha256sum -c SHA256SUMS  # in the directory of the release files
```

Then compare `core` in `manifest.json` with `bin/pins.env` at the tag.

## Reproducing a release

The release flow is: CI builds the artifacts; a second build in the same environment must give the
same hashes; only then is `SHA256SUMS` signed, on the maintainer's machine. Anyone can repeat the
second build:

1. Clone, check out the release tag, verify it, and fetch the submodule (above).
2. Build the Linux natives with `bin/build_linux_container.sh --all` and compare each
   `build/natives/linux-<arch>/libzephyr-java.so` with the copy inside the release JAR
   (`unzip -p zephyr-java-<version>.jar linux-x86_64/libzephyr-java.so | sha256sum`).
3. Build the Android natives with `bin/build_android_container.sh --all`, and compare with the
   libraries in the AAR (`jni/<abi>/libzephyr-java.so`).
4. Compare the jars and the AAR the same way, file by file. `manifest.json` itself records the
   build environment (compilers, glibc, container), so its hash matches only a build in the same
   environment.

Every native of a release is built in the pinned `debian:12` image with the checkout at `/work`,
in CI and locally alike, so the source paths that NDK r18b leaves in the Android libraries read
`/work/...` on both sides and the libraries match. The image installs its packages from the Debian
snapshot `20261107T000000Z` (`snapshot.debian.org`, recorded in the manifest as
`toolchain.debian_snapshot`), so both builds use the same compilers and binutils.
<!-- RELEASES §4 "Release flow" and "Linux build environment"; bin/build_release.sh steps; PLAN §5 R10 result (JAR carries linux-x86_64/libzephyr-java.so) and R11 result; QUESTIONS R11-11 answer (R13 builds Linux in the container only); QUESTIONS R13-6 answer and bin/build_android_container.sh, bin/container-build-deps.sh (DEBIAN_SNAPSHOT), .github/workflows/build.yml natives job (/work) -->

The stripped libraries carry a `gnu_debuglink` to their debug files, which ship in
`debug-symbols-<version>.tar.gz`, so a crash report from a release can be symbolised without
rebuilding.
<!-- bin/strip_natives.sh header (objcopy --only-keep-debug, strip --strip-unneeded, --add-gnu-debuglink); bin/build_release.sh step 6 -->
