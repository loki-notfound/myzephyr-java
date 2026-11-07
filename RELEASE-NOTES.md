# Release notes

One section per release, newest first. Tags are `v<zephyr version>-sec.<N>-java.<M>`; the Maven
version is the tag without the `v`. Changes to the Java API are listed under "Breaking changes".
<!-- RELEASES §2 (versioning; "Breaking changes" section in the release notes) -->

**Not affiliated with ZephyrProtocol.** An independent project. Report Zephyr vulnerabilities to the
ZephyrProtocol team, not here.

## v2.3.0-sec.1-java.1 (unreleased)

The first release of zephyr-java: Java bindings for the Zephyr wallet core (`zephyr-cpp` over
Zephyr's `wallet2`), derived from monero-java at commit `53a54543` (2023-07-10), for Android (AAR)
and for desktop and server JVMs (JAR). The native core is linked statically into one library per
platform, `libzephyr-java.so`, which exports only its JNI entry points.
<!-- README introduction; PLAN §2 (fork point 53a54543, 2023-07-10; native: zephyr-cpp linked statically, only JNI symbols exported); register D17 -->

### Built from

The core is built only from the tagged commits of the zephyr-backports forks:

| | Tag | Commit |
|---|---|---|
| zephyr | `v2.3.0-sec.1` | `e9cf10f796a6053280110dbe453dcd4e4344c8e8` |
| zephyr-cpp | `v2.3.0-sec.1` | `3a2ee602ee28e6b7941b617ad4706349ad992641` |

Both tags are signed with the zephyr-backports release key
`FB39 94F0 997C A6F0 43D7  2491 9EFF 03F5 27D2 40A1`. A build against ZephyrProtocol's original
of the same release (zephyr `v2.3.0` = `67c5f53b878fef41fb5e74c4382d5b7a2f37fd8a`, zephyr-cpp
`4a99272f6d7a171cf6625016939094637459975a`) is expected to work as well.
<!-- bin/pins.env (PIN_TAG, ZEPHYR_SHA, ZEPHYR_CPP_SHA, the key fingerprint and the original's commits in its comments); PLAN §2 "Source pins" and §7.9 -->

### Platforms

- **Android** (`io.github.loki-notfound:zephyr-java-android`, AAR): minSdk 24; native libraries
  for `arm64-v8a`, `armeabi-v7a` and `x86_64`, 16 KB page aligned, stored uncompressed; consumer
  R8 rules included. `armeabi-v7a` needs a CPU with VFPv4. Built with NDK r18b through Zephyr's
  own `contrib/depends`. The AAR and a test app are built and checked, not yet run on a device or
  an emulator.
  <!-- PLAN §5 R9 and R10 results; RELEASES §6 (minSdk 24, final) and §7 (option 1, NDK r18b; armeabi-v7a with -mfpu=vfpv4); README "Status"; android/gradle.properties ARTIFACT_ID -->
- **Linux** (`io.github.loki-notfound:zephyr-java`, JAR): the native libraries for `x86_64` and
  `arm64` inside the JAR, loaded from it. Built in a Debian 12 container (glibc 2.36 or newer:
  Debian 12, Ubuntu 23.04 and later, RHEL 10); everything else is linked statically. The `arm64`
  library is built and checked, not yet run.
  <!-- PLAN §5 R10 and R11 results; PLAN §7.10 (option A); RELEASES §4 "Linux build environment"; README "Status" -->
- **macOS and Windows**: no packaged natives in this release.
  <!-- RELEASES §3 (JAR: "macOS and Windows later"); guide chapter 8 -->

### The dependency

Android (Gradle, Kotlin DSL):

```kotlin
repositories {
    maven { url = uri("https://loki-notfound.github.io/zephyr-java/maven") }
}

android {
    packaging { resources.excludes += "/META-INF/DEPENDENCIES" }
}

dependencies {
    implementation("io.github.loki-notfound:zephyr-java-android:2.3.0-sec.1-java.1")
}
```

The `packaging` line is required in every app: three of the Apache HTTP client's jars carry a
`META-INF/DEPENDENCIES` file, and the app's resource merge fails on the duplicates. Both the AAR
and the JAR bring the same three dependencies: `jackson-core` and `jackson-databind` 2.13.5 (the
last Jackson line for Android below API 26) and `httpclient5` 5.2.1. `jeromq` (only for
`ZephyrWalletRpc`'s ZMQ listener) is optional and must be added by an application that uses it.
The JAR for Maven is `io.github.loki-notfound:zephyr-java:2.3.0-sec.1-java.1`; see the README's
"Coordinates".
<!-- README "Coordinates"; docs/guide/06-android.md "The dependency"; QUESTIONS R10-5 item 1 (META-INF/DEPENDENCIES); QUESTIONS R12-3 answer (artifactId zephyr-java-android, jeromq optional, slf4j-simple test scope); pom.xml and android/build.gradle.kts (the three dependencies); RELEASES §6 (Jackson 2.13.5) -->

### Breaking changes against monero-java

zephyr-java mirrors monero-java's API; a port changes the following. Guide chapter 7, "Coming from
monero-java", has the details and the full renaming table.
<!-- docs/guide/07-coming-from-monero-java.md -->

- **Packages and names:** `monero.*` → `zephyr.*`, `common.*` → `zephyr.lib.*`, classes
  `Monero*` → `Zephyr*`, the native library `monero-java` → `zephyr-java`.
  <!-- PLAN §2 "One-time rename in the source"; register D16; guide chapter 7 "The renaming rules" -->
- **`toAtomicUnits` / `fromAtomicUnits`:** `MoneroUtils.xmrToAtomicUnits(double)` is
  `ZephyrUtils.toAtomicUnits(double)`, `MoneroUtils.atomicUnitsToXmr(BigInteger)` is
  `ZephyrUtils.fromAtomicUnits(BigInteger)`.
  <!-- PLAN §7.3; guide chapter 7 "The renaming rules" -->
- **Balances are per asset:** `getBalance()` returns a `ZephyrBalance`;
  `getBalance(Integer accountIdx)` returns `Map<Integer, ZephyrBalance>` keyed by subaddress;
  `getBalance(String assetType, Integer accountIdx[, Integer subaddressIdx])` returns a
  `BigInteger`. The unlocked balance has the same shapes; `ZephyrAccount` and `ZephyrSubaddress`
  hold a `ZephyrBalance`. `ZephyrBalance.equals` treats an absent asset as zero.
  <!-- PLAN §2 "Balance API"; register D5, D25; guide chapter 7 "Balances are per asset" -->
- **The listener names the asset:** `onBalancesChanged(BigInteger, BigInteger)` became
  `onBalancesChanged(BigInteger, BigInteger, String assetType)`; the old method is `final` and
  deprecated, so a ported listener that overrides it does not compile. Outputs carry the asset in
  `getAssetType()`.
  <!-- register D6; guide chapter 7 "The listener names the asset" -->
- **Every send names a currency pair:** a `ZephyrTxConfig` needs `setSourceCurrency` and
  `setDestinationCurrency`, a plain transfer included; without them the core fails with "Must
  specify source currency". The compiler does not find this one.
  <!-- register D7; guide chapter 7 "Every send names a currency pair" (zephyr-cpp monero_wallet_full.cpp:2102) -->
- **Addresses and constants:** Zephyr's address prefixes (multi-byte varints) and network codes in
  `ZephyrNetworkType`; `ZephyrUtils.RING_SIZE` is 16. Bouncy Castle and commons-codec are no
  longer dependencies (own Keccak-256 and hex).
  <!-- register D9, D10, D24 -->
- **The RPC classes are a first pass:** `ZephyrWalletRpc` serves the base coin and rejects the
  per-asset calls, reserve info and supply with a clear message.
  <!-- register D11 -->
- **Dependencies:** `slf4j-simple` is no longer a runtime dependency (test scope only), and
  `jeromq` is optional.
  <!-- QUESTIONS R12-3 answer; register D26 -->

New in zephyr-java: `getReserveInfo()` and `getCirculatingSupply()` on the wallet, `ZephyrAssets`
with the asset names, and the pricing record on daemon block headers.
<!-- register D5, D8, D12; guide chapter 7 "New calls" -->

### Tested

The full wallet ran on mainnet against a node with real funds: create, open, sync, balances per
asset, transfers, a ZPH → ZRS conversion and sweeps (five transactions, verified on the node).
The pure test suite has 30 tests. Conversions other than ZPH → ZRS are not tested on mainnet.
<!-- PLAN §5 R8 results (both parts: five transactions, all verified on the node) and R12 result (30 tests green); guide chapter 8 "Conversions other than ZPH → ZRS are not tested on mainnet" -->

### Known limitations

Guide chapter 8, [Known limitations](docs/guide/08-known-limitations.md), lists them with their
status. The ones a wallet app meets first: `getOutputs()` reports no asset; the asset of an
unconfirmed output notification is empty; `checkTxKey` returns amount 0; on Android, TLS to a node
needs the app's help (the native wallet does not use Android's certificate store).
<!-- docs/guide/08-known-limitations.md (headings "getOutputs() reports no asset", "The asset of an unconfirmed output notification is empty", "checkTxKey returns amount 0", "Android: TLS needs the app's help"); register D23 -->

### Verification

The release tag and `SHA256SUMS` are signed with the maintainer key
`31DA 39AB 43E0 CA67 F1F3  A661 BA2A FF22 DF8A 4A13`; its public key is `KEYS.asc` in the
repository.

```sh
gpg --import KEYS.asc
git verify-tag v2.3.0-sec.1-java.1
gpg --verify SHA256SUMS.asc && sha256sum -c SHA256SUMS
```

The Maven repository and the site are the archive `site-<version>.tar.gz`, whose hash is in
`SHA256SUMS`; Pages serves its contents.
<!-- PLAN §7.8; RELEASES §3; README "Verifying a release" (the same commands); bin/package_release.sh step 7 (the site archive) -->

### Build manifest

`manifest.json` among the release files records the zephyr-java commit and its
`SOURCE_DATE_EPOCH`, both core pins (tag, commit, URL), the build-layer patches, the toolchain
(compilers, NDK, CMake, JDK, Maven, Gradle and the Android Gradle plugin, the Linux container
image and its glibc), every depends package with its sha256, and the size and sha256 of every
release file. Every artifact is built twice in the same environment, and the hashes must match
before `SHA256SUMS` is signed. Guide chapter 9 explains how to repeat the build.
<!-- bin/make_manifest.sh header; RELEASES §4 "Release flow"; docs/guide/09-building-and-verifying.md "The manifest" and "Reproducing a release" -->
