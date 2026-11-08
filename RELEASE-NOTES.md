# Release notes

One section per release, newest first. Tags are `v<zephyr version>-sec.<N>-java.<M>`; the Maven
version is the tag without the `v`. Changes to the Java API are listed under "Breaking changes".
A release's date is the date of its signed tag and of its GitHub release.

**Not affiliated with ZephyrProtocol.** An independent project. Report Zephyr vulnerabilities to the
ZephyrProtocol team, not here.

## v2.3.0-sec.2-java.3

The third release on the same core as `v2.3.0-sec.2-java.1`: a fix in the JNI bridge for
debuggable Android apps, a clear error for the RPC classes on Android 7, and the Android
documentation. No breaking change.

### Built from

The same zephyr-backports release set of 2026-11-07 as the previous releases, unchanged:

| | Tag | Commit | Tag subject |
|---|---|---|---|
| zephyr | `v2.3.0-sec.2` | `8c6a120256b4bd887d6e7167d929d76e3507e0e5` | zephyr v2.3.0 + 30 security backports from Monero |
| zephyr-cpp | `v2.3.0-sec.1` | `3a2ee602ee28e6b7941b617ad4706349ad992641` | zephyr-cpp master (4a99272f6d7a171cf6625016939094637459975a) + 4 security backports from monero-cpp |

### Fixed

- The JNI bridge no longer releases `null` strings. Debuggable Android apps, in which ART's
  CheckJNI is always on, aborted when a wallet was created from a mnemonic or keys, or opened,
  with a connection without credentials: the bridge released the connection's `null` user name
  and password. This affected `v2.3.0-sec.2-java.1` and `v2.3.0-sec.2-java.2`; release builds and
  the JVM (without `-Xcheck:jni`) were not affected. Every string release in the bridge now checks
  for `null` first, and the natives that read a path, a password or a byte array accept `null`
  like the others.
- Memory leaks in four calls of the native bridge, `ZephyrUtils.binaryToMap`,
  `ZephyrUtils.binaryBlocksToMap`, `ZephyrWalletFull.getSubaddresses` and
  `getAddressBookEntries`, which never released the array passed to them, and in `changePassword`,
  which never released the two password strings.

### Changed

- The RPC classes (`ZephyrDaemonRpc`, `ZephyrWalletRpc`) report a missing `java.time` on
  Android 7.x (API 24 and 25) with a `ZephyrError` that names the remedy, Android 8.0 or core
  library desugaring in the app, on every request, instead of a bare `NoClassDefFoundError`.

### Documentation

- Guide chapter 6 (Android) completed: where the repository line goes, with the Groovy and
  version catalog forms; the RPC classes below API 26 and the desugaring lines; how to check which
  Jackson the app gets; ABI sizes and App Bundles; the `INTERNET` permission and clear text; Auto
  Backup and wallet data; logs; a first smoke test; where the libraries have run. The README and
  chapter 8 follow.

### Tested

- On the Android emulator (`x86_64`), with this release's AAR in debug builds (CheckJNI on), on
  API 24 and on API 35 with 16 KB pages: the offline wallet; a wallet restored from its mnemonic
  and synced against a mainnet node, then opened again from its bytes with a connection without
  credentials; `ZephyrDaemonRpc` on API 35, and on API 24 with core library desugaring; without
  desugaring, the new error on API 24. The ARM libraries have not yet run on hardware.

### The dependency

Android: `io.github.loki-notfound:zephyr-java-android:2.3.0-sec.2-java.3`; the JVM:
`io.github.loki-notfound:zephyr-java:2.3.0-sec.2-java.3`. The repository line, the `packaging`
line and the dependencies are those of the earlier releases; guide chapter 6 says where they go.

### Verification

```sh
gpg --import KEYS.asc
git verify-tag v2.3.0-sec.2-java.3
gpg --verify SHA256SUMS.asc && sha256sum -c SHA256SUMS
```

## v2.3.0-sec.2-java.2

The second release on the same core as `v2.3.0-sec.2-java.1`: a fix for the Android `x86_64`
library, a helper for the asset of an output, and build changes. No breaking change.

### Built from

The same zephyr-backports release set of 2026-11-07 as the previous release, unchanged:

| | Tag | Commit | Tag subject |
|---|---|---|---|
| zephyr | `v2.3.0-sec.2` | `8c6a120256b4bd887d6e7167d929d76e3507e0e5` | zephyr v2.3.0 + 30 security backports from Monero |
| zephyr-cpp | `v2.3.0-sec.1` | `3a2ee602ee28e6b7941b617ad4706349ad992641` | zephyr-cpp master (4a99272f6d7a171cf6625016939094637459975a) + 4 security backports from monero-cpp |

### Fixed

- The `x86_64` library in the AAR no longer needs an executable stack: every native library is
  now linked with `-z noexecstack` (the core's assembly files carry no non-executable-stack note,
  and NDK r18b's linker marked that library as needing one).

### Added

- `ZephyrWallet.getOutputAssetType(output)`: the asset of an output from `getOutputs()`, which
  the core does not report, read from the output's transaction in the wallet's history. Guide
  chapter 8 has the rule and its limits.
- Javadoc starting "Zephyr:" on `checkTxKey`, `checkTxProof`, `ZephyrCheckTx.getReceivedAmount()`,
  `getOutputs()` and `ZephyrOutputWallet.getAssetType()`: the amount of a payment check is 0 and
  an output carries no asset in the full wallet, why, and the workaround.

### Build

- The developer build (`bin/build_libzephyr_java.sh`) compiles for the generic CPU instead of the
  build machine's, so a library or a compiler cache made on one machine runs on another.
- The Maven repository on Pages keeps every release: this release's site carries
  `2.3.0-sec.2-java.1` next to `2.3.0-sec.2-java.2`, and each `maven-metadata.xml` lists both.

### The dependency

Android: `io.github.loki-notfound:zephyr-java-android:2.3.0-sec.2-java.2`; the JVM:
`io.github.loki-notfound:zephyr-java:2.3.0-sec.2-java.2`. The repository line, the `packaging`
line and the dependencies are those of the previous release, below.

### Verification

```sh
gpg --import KEYS.asc
git verify-tag v2.3.0-sec.2-java.2
gpg --verify SHA256SUMS.asc && sha256sum -c SHA256SUMS
```

## v2.3.0-sec.2-java.1

The first release of zephyr-java: Java bindings for the Zephyr wallet core (`zephyr-cpp` over
Zephyr's `wallet2`), derived from monero-java at commit `53a54543` (2023-07-10), for Android (AAR)
and for desktop and server JVMs (JAR). The native core is linked statically into one library per
platform, `libzephyr-java.so`, which exports only its JNI entry points.

### Built from

The core is built only from the tagged commits of the zephyr-backports forks:

| | Tag | Commit | Tag subject |
|---|---|---|---|
| zephyr | `v2.3.0-sec.2` | `8c6a120256b4bd887d6e7167d929d76e3507e0e5` | zephyr v2.3.0 + 30 security backports from Monero |
| zephyr-cpp | `v2.3.0-sec.1` | `3a2ee602ee28e6b7941b617ad4706349ad992641` | zephyr-cpp master (4a99272f6d7a171cf6625016939094637459975a) + 4 security backports from monero-cpp |

These two tags belong to the zephyr-backports **release set of 2026-11-07**, which also names
zephyr-javascript `v2.3.0-sec.1` (`7b17a14280bd84ca783287f4640b3c744d25319f`) and zephyr-wallet
`v2.3.0-sec.1` (`edef8c0ee281024de1a718aa6dce39bce75f3687`); zephyr-java does not build those two.
The backports number each fork on its own: `sec.<N>` in this release's version is zephyr's tag
number, and the zephyr-cpp tag is named here and in `manifest.json` (`core.zephyr-cpp.tag`, next
to `core.release_set` and its members).

All tags are signed with the zephyr-backports release key
`FB39 94F0 997C A6F0 43D7  2491 9EFF 03F5 27D2 40A1`. A build against ZephyrProtocol's original
of the same release (zephyr `v2.3.0` = `67c5f53b878fef41fb5e74c4382d5b7a2f37fd8a`, zephyr-cpp
`4a99272f6d7a171cf6625016939094637459975a`) is expected to work as well.

### Platforms

- **Android** (`io.github.loki-notfound:zephyr-java-android`, AAR): minSdk 24; native libraries
  for `arm64-v8a`, `armeabi-v7a` and `x86_64`, 16 KB page aligned, stored uncompressed; consumer
  R8 rules included. `armeabi-v7a` needs a CPU with VFPv4. Built with NDK r18b through Zephyr's
  own `contrib/depends`. The AAR is built and checked (16 KB page alignment, uncompressed native
  libraries) in a throwaway consumer app; it has not yet run on a device or an emulator.
- **Linux** (`io.github.loki-notfound:zephyr-java`, JAR): the native libraries for `x86_64` and
  `arm64` inside the JAR, loaded from it. Built in a Debian 12 container (glibc 2.36 or newer:
  Debian 12, Ubuntu 23.04 and later, RHEL 10); everything else is linked statically. The `arm64`
  library is built and checked, not yet run.
- **macOS and Windows**: no packaged natives in this release.

### The dependency

Android (Gradle, Kotlin DSL):

```kotlin
repositories {
    maven { url = uri("https://loki-notfound.github.io/myzephyr-java/maven") }
}

android {
    packaging { resources.excludes += "/META-INF/DEPENDENCIES" }
}

dependencies {
    implementation("io.github.loki-notfound:zephyr-java-android:2.3.0-sec.2-java.1")
}
```

The `packaging` line is required in every app: three of the Apache HTTP client's jars carry a
`META-INF/DEPENDENCIES` file, and the app's resource merge fails on the duplicates. Both the AAR
and the JAR bring the same three dependencies: `jackson-core` and `jackson-databind` 2.13.5 (the
last Jackson line for Android below API 26) and `httpclient5` 5.2.1. `jeromq` (only for
`ZephyrWalletRpc`'s ZMQ listener) is optional and must be added by an application that uses it.
The JAR for Maven is `io.github.loki-notfound:zephyr-java:2.3.0-sec.2-java.1`; see the README's
"Coordinates".

### Breaking changes against monero-java

zephyr-java mirrors monero-java's API; a port changes the following. Guide chapter 7, "Coming from
monero-java", has the details and the full renaming table.

- **Packages and names:** `monero.*` → `zephyr.*`, `common.*` → `zephyr.lib.*`, classes
  `Monero*` → `Zephyr*`, the native library `monero-java` → `zephyr-java`.
- **`toAtomicUnits` / `fromAtomicUnits`:** `MoneroUtils.xmrToAtomicUnits(double)` is
  `ZephyrUtils.toAtomicUnits(double)`, `MoneroUtils.atomicUnitsToXmr(BigInteger)` is
  `ZephyrUtils.fromAtomicUnits(BigInteger)`.
- **Balances are per asset:** `getBalance()` returns a `ZephyrBalance`;
  `getBalance(Integer accountIdx)` returns `Map<Integer, ZephyrBalance>` keyed by subaddress;
  `getBalance(String assetType, Integer accountIdx[, Integer subaddressIdx])` returns a
  `BigInteger`. The unlocked balance has the same shapes; `ZephyrAccount` and `ZephyrSubaddress`
  hold a `ZephyrBalance`. `ZephyrBalance.equals` treats an absent asset as zero.
- **The listener names the asset:** `onBalancesChanged(BigInteger, BigInteger)` became
  `onBalancesChanged(BigInteger, BigInteger, String assetType)`; the old method is `final` and
  deprecated, so a ported listener that overrides it does not compile. Outputs carry the asset in
  `getAssetType()`.
- **Every send names a currency pair:** a `ZephyrTxConfig` needs `setSourceCurrency` and
  `setDestinationCurrency`, a plain transfer included; without them the core fails with "Must
  specify source currency". The compiler does not find this one.
- **Addresses and constants:** Zephyr's address prefixes (multi-byte varints) and network codes in
  `ZephyrNetworkType`; `ZephyrUtils.RING_SIZE` is 16. Bouncy Castle and commons-codec are no
  longer dependencies (own Keccak-256 and hex).
- **The RPC classes are a first pass:** `ZephyrWalletRpc` serves the base coin and rejects the
  per-asset calls, reserve info and supply with a clear message.
- **Dependencies:** `slf4j-simple` is no longer a runtime dependency (test scope only), and
  `jeromq` is optional.

New in zephyr-java: `getReserveInfo()` and `getCirculatingSupply()` on the wallet, `ZephyrAssets`
with the asset names, and the pricing record on daemon block headers.

### Tested

The full wallet ran on mainnet against a node with real funds: create, open, sync, balances per
asset, transfers, a ZPH → ZRS conversion and sweeps (five transactions, verified on the node).
The pure test suite has 30 tests. Conversions other than ZPH → ZRS are not tested on mainnet.

### Known limitations

Guide chapter 8, [Known limitations](docs/guide/08-known-limitations.md), lists them with their
status. The ones a wallet app meets first: `getOutputs()` reports no asset; the asset of an
unconfirmed output notification is empty; `checkTxKey` returns amount 0; on Android, TLS to a node
needs the app's help (the native wallet does not use Android's certificate store).

### Known issue

The `x86_64` library in the AAR (`jni/x86_64/libzephyr-java.so`) is marked as needing an
executable stack: NDK r18b's linker does that for the core's assembly files, which carry no
non-executable-stack note. Android may refuse to load it, or it weakens the hardening of the app on
`x86_64` devices and emulators; the other libraries are not affected, and the next release fixes it.
Fixed in v2.3.0-sec.2-java.2.

### Verification

The release tag and `SHA256SUMS` are signed with the maintainer key
`31DA 39AB 43E0 CA67 F1F3  A661 BA2A FF22 DF8A 4A13`; its public key is `KEYS.asc` in the
repository.

```sh
gpg --import KEYS.asc
git verify-tag v2.3.0-sec.2-java.1
gpg --verify SHA256SUMS.asc && sha256sum -c SHA256SUMS
```

The Maven repository and the site are the archive `site-<version>.tar.gz`, whose hash is in
`SHA256SUMS`; Pages serves its contents.

### Build manifest

`manifest.json` among the release files records the zephyr-java commit and its
`SOURCE_DATE_EPOCH`, both core pins (tag, commit, URL), the build-layer patches, the toolchain
(compilers, NDK, CMake, JDK, Maven, Gradle and the Android Gradle plugin, the Linux container
image and its glibc), every depends package with its sha256, and the size and sha256 of every
release file. Every artifact is built twice in the same environment, and the hashes must match
before `SHA256SUMS` is signed. Guide chapter 9 explains how to repeat the build.
