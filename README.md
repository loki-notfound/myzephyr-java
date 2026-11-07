# zephyr-java

Java bindings for the [Zephyr Protocol](https://github.com/ZephyrProtocol) wallet core, for Android
(AAR) and for desktop and server JVMs (JAR). The native core is `zephyr-cpp` over Zephyr's `wallet2`,
built from the security-hardened forks of [zephyr-backports](https://github.com/zephyr-backports),
linked statically into one native library per platform. The Java API mirrors
[monero-java](https://github.com/woodser/monero-java), from which this project is derived, with
Zephyr's assets, conversions and reserve information added.
<!-- pins: bin/pins.env (zephyr v2.3.0-sec.2 and zephyr-cpp v2.3.0-sec.1, the zephyr-backports release set of 2026-11-07); static linking: register D17 -->

**Not affiliated with ZephyrProtocol.** An independent project. Report Zephyr vulnerabilities to the
ZephyrProtocol team, not here.

## Status

Pre-release: there is no release yet. The first release, `v2.3.0-sec.2-java.1`, is built from the
zephyr-backports release set of 2026-11-07: zephyr `v2.3.0-sec.2` and zephyr-cpp `v2.3.0-sec.1`.
<!-- RELEASES §2; PLAN §7.9; bin/pins.env (ZEPHYR_TAG, ZEPHYR_CPP_TAG, RELEASE_SET) -->

What works today, on the development branches:

- **The full wallet on mainnet**: create, open, sync, balances per asset, transfers, a ZPH → ZRS
  conversion and sweeps, run against a mainnet node with real funds.
  <!-- PLAN §5, R8 results (both parts): TestZephyrIntegration and TestZephyrIntegrationFunds -->
- **The AAR** for Android, minSdk 24, with the native library for `arm64-v8a`, `armeabi-v7a` and
  `x86_64`. The `armeabi-v7a` library needs a CPU with VFPv4, which every device that runs
  Android 7 has. The AAR is built and checked (16 KB page alignment, uncompressed native libraries)
  in a throwaway consumer app; it has not yet run on a device or an emulator.
  <!-- PLAN §5 R9 and R10 results; RELEASES §6 (minSdk 24) and §7 (VFPv4); evidence/r9/armv7-checks.txt; evidence/r13/release-candidate-r13.md (the consumer app check: AGP 9.5.0, zipalign -c -P 16, the three libraries stored) -->
- **The JAR** with the native libraries for Linux `x86_64` and `arm64` inside, loaded from the JAR.
  Release natives are built in a Debian 12 container and need glibc 2.36 or newer (Debian 12,
  Ubuntu 23.04 and later, RHEL 10); everything else is linked statically. The `arm64` library is
  built and checked, not yet run.
  <!-- PLAN §5 R10 and R11 results; PLAN §7.10 (option A); RELEASES §4 "Linux build environment" -->
- **The RPC classes** (`ZephyrWalletRpc`, `ZephyrDaemonRpc`) are a first pass only: the wallet RPC
  client serves the base coin and rejects the per-asset calls with a clear message.
  <!-- register D11; PLAN §5 R7 result -->

## Coordinates

Releases are published as GitHub releases and in a static Maven repository on this project's
GitHub Pages, `https://loki-notfound.github.io/zephyr-java/maven` (available with the first
release). Maven Central may follow once the API has settled. The repository and the Javadoc live on
the project's GitHub Pages, built by `bin/make_site.sh` from each release (`dist/<version>/site/`)
and published with the first release.
<!-- RELEASES §5; PLAN §2 "Distribution"; bin/make_maven_repo.sh, bin/make_site.sh -->

Android (Gradle, Kotlin DSL). The AAR has its own artifactId, `zephyr-java-android`, and brings
its POM's dependencies (Jackson and the Apache HTTP client) along:

```kotlin
repositories {
    maven { url = uri("https://loki-notfound.github.io/zephyr-java/maven") }
}

dependencies {
    implementation("io.github.loki-notfound:zephyr-java-android:2.3.0-sec.2-java.1")
}
```
<!-- artifactId: android/gradle.properties ARTIFACT_ID (QUESTIONS R12-3 answer); AAR POM dependencies: android/build.gradle.kts (jackson-core, jackson-databind, httpclient5); PLAN §5 R10 result -->

Every Android app that uses the AAR also needs this line in its `android { }` block:

```kotlin
packaging { resources.excludes += "/META-INF/DEPENDENCIES" }
```

Three of the Apache HTTP client's jars carry a file `META-INF/DEPENDENCIES`, and the app's resource
merge fails on the duplicates. The file is Maven metadata that nothing reads at run time. A library
cannot fix this for the app.
<!-- QUESTIONS R10-5 item 1 (mergeDebugJavaResource, httpclient5, httpcore5, httpcore5-h2); PLAN §5 R10 result -->

Desktop and server (Maven), the JAR with the Linux natives:

```xml
<repositories>
  <repository>
    <id>zephyr-java</id>
    <url>https://loki-notfound.github.io/zephyr-java/maven</url>
  </repository>
</repositories>

<dependency>
  <groupId>io.github.loki-notfound</groupId>
  <artifactId>zephyr-java</artifactId>
  <version>2.3.0-sec.2-java.1</version>
</dependency>
```
<!-- pom.xml groupId and artifactId; RELEASES §2 (Maven version = tag without the v) -->

## Quick start

`src/test/java/examples/QuickStart.java` opens or creates a mainnet wallet, syncs it with a
listener that prints the progress, prints the balance of the four assets and, with `--send`,
builds a ZPH transfer and prints its hash and fee **without relaying it**. It is compiled with the
tests on every `mvn test`, and a test checks that this README quotes it unchanged.

```java
package examples;

import java.math.BigDecimal;
import java.math.BigInteger;
import zephyr.daemon.model.ZephyrNetworkType;
import zephyr.wallet.ZephyrWalletFull;
import zephyr.wallet.model.ZephyrAssets;
import zephyr.wallet.model.ZephyrBalance;
import zephyr.wallet.model.ZephyrTxConfig;
import zephyr.wallet.model.ZephyrTxWallet;
import zephyr.wallet.model.ZephyrWalletConfig;
import zephyr.wallet.model.ZephyrWalletListener;

/**
 * Opens or creates a mainnet wallet, syncs it, prints the balance per asset and, with --send,
 * builds a ZPH transfer without relaying it. This example never spends.
 *
 * Usage: QuickStart <wallet path> <password> <daemon URI> [--send <address> <amount in ZPH>]
 */
public class QuickStart {

  public static void main(String[] args) {
    boolean send = args.length == 6 && args[3].equals("--send");
    if (args.length != 3 && !send) {
      System.err.println("Usage: QuickStart <wallet path> <password> <daemon URI> [--send <address> <amount in ZPH>]");
      System.exit(2);
    }
    String path = args[0];
    String password = args[1];
    String daemonUri = args[2];

    // open the wallet files at the path, or create a new random wallet there
    ZephyrWalletFull wallet = ZephyrWalletFull.walletExists(path)
        ? ZephyrWalletFull.openWallet(path, password, ZephyrNetworkType.MAINNET, daemonUri)
        : ZephyrWalletFull.createWallet(new ZephyrWalletConfig()
            .setPath(path)
            .setPassword(password)
            .setNetworkType(ZephyrNetworkType.MAINNET)
            .setServerUri(daemonUri));
    try {
      System.out.println("Primary address: " + wallet.getPrimaryAddress());

      // sync; the listener runs on a native thread, once per block fetched
      wallet.sync(new ZephyrWalletListener() {
        @Override
        public void onSyncProgress(long height, long startHeight, long endHeight, double percentDone, String message) {
          System.out.printf("Synced block %d of %d (%.1f%%)%n", height, endHeight - 1, percentDone * 100);
        }
      });

      // the balance per asset; amounts are atomic units, 10^12 per coin for every asset
      ZephyrBalance balance = wallet.getBalance();
      ZephyrBalance unlocked = wallet.getUnlockedBalance();
      for (String asset : ZephyrAssets.V2) {
        System.out.println(asset + ": " + toCoins(balance.get(asset)) + " (unlocked " + toCoins(unlocked.get(asset)) + ")");
      }

      // build a plain ZPH transfer: every send names the source and the destination currency
      if (send) {
        ZephyrTxWallet tx = wallet.createTx(new ZephyrTxConfig()
            .setAddress(args[4])
            .setAmount(new BigDecimal(args[5]).movePointRight(12).toBigIntegerExact())
            .setSourceCurrency(ZephyrAssets.ZPH)
            .setDestinationCurrency(ZephyrAssets.ZPH)
            .setAccountIndex(0)
            .setRelay(false));  // built and signed, never broadcast
        System.out.println("Built, not relayed: tx " + tx.getHash() + ", fee " + toCoins(tx.getFee()) + " ZPH");
      }
      wallet.save();
    } finally {
      wallet.close();
    }
  }

  private static String toCoins(BigInteger atomicUnits) {
    return new BigDecimal(atomicUnits, 12).toPlainString();
  }
}
```

Every send names a source and a destination currency; a plain transfer uses the same asset for
both, a conversion two different ones. The [user guide](docs/guide/README.md) explains the
pairs, the fees and the locks.
<!-- ZEPHYR-DELTA §1 "Tx config: sourceCurrency and destinationCurrency are required for every send" -->

## Documentation

- [User guide](docs/guide/README.md):
  [1 Getting started](docs/guide/01-getting-started.md),
  [2 Wallets](docs/guide/02-wallets.md),
  [3 Assets and balances](docs/guide/03-assets-and-balances.md),
  [4 Sending and converting](docs/guide/04-sending-and-converting.md),
  [5 Reserve info and circulating supply](docs/guide/05-reserve-and-supply.md),
  [6 Android](docs/guide/06-android.md),
  [7 Coming from monero-java](docs/guide/07-coming-from-monero-java.md),
  [8 Known limitations](docs/guide/08-known-limitations.md),
  [9 Building and verifying](docs/guide/09-building-and-verifying.md).
- Javadoc: on this project's GitHub Pages with the first release, and as the `-javadoc.jar` of
  every release; `mvn javadoc:javadoc` builds it from a checkout.
- Releases: <https://github.com/loki-notfound/zephyr-java/releases>.

## Building from source

The native core is built only from pinned commits. `bin/pins.env` names them: zephyr at the
zephyr-backports tag `v2.3.0-sec.2` and zephyr-cpp at `v2.3.0-sec.1` (the release set of
2026-11-07), by sha, never a branch. A build against ZephyrProtocol's original of the same
release (zephyr `v2.3.0`, zephyr-cpp `4a99272`) must work as well; the backports are the same
software plus Monero security fixes.
<!-- bin/pins.env; PLAN §2 "Source pins" -->

```sh
git clone https://github.com/loki-notfound/zephyr-java.git
cd zephyr-java
git submodule update --init external/zephyr-cpp
```

- `bin/build_libzephyr_java.sh`: the quick developer build. It links against the host's shared
  libraries (boost, OpenSSL, unbound and others), so the result runs only on hosts like the one
  that built it. Then `mvn test` runs the pure test suites against it.
- `bin/build_linux_container.sh`: the Linux release natives (`x86_64`, `arm64`), built with
  zephyr's `contrib/depends` in the pinned `debian:12` image, with podman or docker.
- `bin/build_android.sh`: the Android natives per ABI with zephyr's `contrib/depends` and NDK r18b.
- `bin/build_release.sh`: everything, into `dist/<version>/`: the JAR, the AAR, sources, Javadoc,
  debug symbols, `manifest.json` and `SHA256SUMS`.

`manifest.json` records the backports tags and shas, the zephyr-java commit and its
`SOURCE_DATE_EPOCH`, every depends package with its sha256, the build-layer patches, the
toolchain versions and the sha256 of every artifact. Chapter 9 of the guide has the details and
the steps to reproduce a release.
<!-- bin/build_*.sh header comments; bin/make_manifest.sh; dist/ manifest of R11 run 1 (keys core, depends, patches, toolchain, zephyr_java, artifacts) -->

## Verifying a release

Release tags and `SHA256SUMS` are signed with the maintainer key:

```
31DA 39AB 43E0 CA67 F1F3  A661 BA2A FF22 DF8A 4A13
```

The public key is [`KEYS.asc`](KEYS.asc) in this repository, and from the first release on
keys.openpgp.org as well. To check a release:
<!-- PLAN §7.8; RELEASES §3 (SHA256SUMS + signature) -->

```sh
gpg --import KEYS.asc
git verify-tag v2.3.0-sec.2-java.1
gpg --verify SHA256SUMS.asc && sha256sum -c SHA256SUMS
```

`SHA256SUMS` covers every release file, `manifest.json` included. The Maven repository and the
site are the archive `site-<version>.tar.gz`, whose hash is in `SHA256SUMS`; Pages serves its
contents. The release flow builds every artifact twice in the same environment and the hashes must
match before the owner signs.
<!-- bin/make_manifest.sh header ("SHA256SUMS is sha256sum over the top-level files"); bin/package_release.sh step 7 (the site archive); RELEASES §4 "Release flow" -->

The core's tags are signed by zephyr-backports with its release key,
`FB39 94F0 997C A6F0 43D7  2491 9EFF 03F5 27D2 40A1` (`keys/zephyr-backports.asc` in the
zephyr-backports repository). The build scripts run `git verify-tag` on them when the tag is
present locally; without gpg or without that key in the keyring they print a warning and build
anyway, so that a build without the keys still works.
<!-- bin/pins.env comment; verify_pin_tag in bin/build_libzephyr_java.sh, bin/build_linux.sh, bin/build_android.sh -->

## Versioning

A release is tagged `v<zephyr version>-sec.<N>-java.<M>`, for example `v2.3.0-sec.2-java.1`; the
Maven version is the same without the `v`.

- `<zephyr version>-sec.<N>` names the zephyr-backports tag of the core the release is built
  from, and with it the network rules and hard forks the release supports. The backports number
  each fork on its own, so `sec.<N>` is the number of **zephyr's** tag (the network core); the
  zephyr-cpp tag is named in the tag message, in `manifest.json` and in this README.
- `java.<M>` counts the zephyr-java releases on that core and starts again at 1 for each new
  core tag.
- The version does not encode API compatibility. Changes to the Java API are listed under
  "Breaking changes" in the release notes.
- Tags are annotated and signed, and never moved or reused.
<!-- RELEASES §2 including the per-fork refinement (owner agreed 2026-11-07) -->

## Credits and licence

Derived from monero-java by woodser (MIT), whose copyright notices are kept in the source files.
zephyr-java is MIT licensed as well; see `LICENCE.txt`.

## Security

Vulnerabilities in the Zephyr core (zephyr, zephyr-cpp, `wallet2`) go to the ZephyrProtocol team.
Vulnerabilities in zephyr-java itself (the Java code, the JNI bridge, the build and the release
artifacts) go to `loki@lateo.dev`.

## Donations

If zephyr-java is useful to you, donations are welcome at this ZEPH address:

```
ZEPHs8MmrN556ieVvv9YUZf3J5pW2uN1z5S88mb9DksG9bcEcqVGg9XbuQc6Q9e9Ka6bjMHYgaSidWfL1r1DGLyngj8NHT3Q6gp
```
