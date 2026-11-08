# zephyr-java

Repository: `loki-notfound/myzephyr-java`. The library and its artifacts are named `zephyr-java`
(`io.github.loki-notfound:zephyr-java` and `:zephyr-java-android`); only the repository carries the
`my` prefix.

Java bindings for the [Zephyr Protocol](https://github.com/ZephyrProtocol) wallet core, for Android
(AAR) and for desktop and server JVMs (JAR). The native core is `zephyr-cpp` over Zephyr's `wallet2`,
built from the security-hardened forks of [zephyr-backports](https://github.com/zephyr-backports),
linked statically into one native library per platform. The Java API mirrors
[monero-java](https://github.com/woodser/monero-java), from which this project is derived, with
Zephyr's assets, conversions and reserve information added.

**Not affiliated with ZephyrProtocol.** An independent project. Report Zephyr vulnerabilities to the
ZephyrProtocol team, not here.

## Status

The current release is `v2.3.0-sec.2-java.3`, built from the zephyr-backports release set of
2026-11-07: zephyr `v2.3.0-sec.2` and zephyr-cpp `v2.3.0-sec.1`.

What works:

- **The full wallet on mainnet**: create, open, sync, balances per asset, transfers, a ZPH → ZRS
  conversion and sweeps, run against a mainnet node with real funds.
- **The AAR** for Android, minSdk 24, with the native library for `arm64-v8a`, `armeabi-v7a` and
  `x86_64`. The `armeabi-v7a` library needs a CPU with VFPv4, which every device that runs
  Android 7 has. The `x86_64` library and the wallet run on the Android emulator (API 24, and
  API 35 with 16 KB pages), a release check; the ARM libraries have not yet run on hardware. The
  RPC classes need API 26 or core library desugaring in the app.
- **The JAR** with the native libraries for Linux `x86_64` and `arm64` inside, loaded from the JAR.
  Release natives are built in a Debian 12 container and need glibc 2.36 or newer (Debian 12,
  Ubuntu 23.04 and later, RHEL 10); everything else is linked statically. The `arm64` library is
  built and checked, not yet run.
- **The RPC classes** (`ZephyrWalletRpc`, `ZephyrDaemonRpc`) are a first pass only: the wallet RPC
  client serves the base coin and rejects the per-asset calls with a clear message.

## Coordinates

Releases are published as GitHub releases and in a static Maven repository on this project's
GitHub Pages, `https://loki-notfound.github.io/myzephyr-java/maven`. Maven Central may follow once
the API has settled. The repository and the Javadoc live on the project's GitHub Pages, built by
`bin/make_site.sh` from each release (`dist/<version>/site/`); the repository keeps every release.

Android (Gradle, Kotlin DSL). The AAR has its own artifactId, `zephyr-java-android`, and brings
its POM's dependencies (Jackson and the Apache HTTP client) along. The repository goes into
`settings.gradle.kts`, the dependency into the app module's `build.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://loki-notfound.github.io/myzephyr-java/maven") }
    }
}
```

```kotlin
dependencies {
    implementation("io.github.loki-notfound:zephyr-java-android:2.3.0-sec.2-java.3")
}
```

Every Android app that uses the AAR also needs this line in its `android { }` block:

```kotlin
packaging { resources.excludes += "/META-INF/DEPENDENCIES" }
```

Three of the Apache HTTP client's jars carry a file `META-INF/DEPENDENCIES`, and the app's resource
merge fails on the duplicates. The file is Maven metadata that nothing reads at run time. A library
cannot fix this for the app.

The app declares the `INTERNET` permission to reach a node. On API 24 and 25 the RPC classes also
need core library desugaring in the app; the native wallet does not.
[Chapter 6 of the user guide](docs/guide/06-android.md) has the Groovy and version catalog forms,
the desugaring lines, ABIs and sizes, backup, logs and a first test.

Desktop and server (Maven), the JAR with the Linux natives:

```xml
<repositories>
  <repository>
    <id>zephyr-java</id>
    <url>https://loki-notfound.github.io/myzephyr-java/maven</url>
  </repository>
</repositories>

<dependency>
  <groupId>io.github.loki-notfound</groupId>
  <artifactId>zephyr-java</artifactId>
  <version>2.3.0-sec.2-java.3</version>
</dependency>
```

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

## Known limitations

- **`checkTxKey` returns amount 0.** The core reads the received amount of the old asset `ZEPH`
  only, so for every current transaction the amount is 0 while `isGood()` and the confirmations are
  right. Workaround: read the amount per asset from `getTxs(hash)`, the receiver's incoming
  transfers or the sender's destinations
  ([guide](docs/guide/08-known-limitations.md#checktxkey-returns-amount-0)).
- **`getOutputs()` reports no asset.** The core does not serialize an output's asset, so
  `getAssetType()` of every output is `null`. Workaround: take the asset from the output's
  transaction in `getTxs(hash)`
  ([guide](docs/guide/08-known-limitations.md#getoutputs-reports-no-asset)).
- **TLS on Android needs the app's help.** The native wallet does not use Android's certificate
  store. Workaround: set `SSL_CERT_FILE` or `SSL_CERT_DIR` before connecting to an `https://` node
  ([guide](docs/guide/06-android.md#tls-to-a-node)).

[Chapter 8 of the guide](docs/guide/08-known-limitations.md) has the full list, with code.

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
- Javadoc: on this project's GitHub Pages (the current release), and as the `-javadoc.jar` of
  every release; `mvn javadoc:javadoc` builds it from a checkout.
- Releases: <https://github.com/loki-notfound/myzephyr-java/releases>.

## Building from source

The native core is built only from pinned commits. `bin/pins.env` names them: zephyr at the
zephyr-backports tag `v2.3.0-sec.2` and zephyr-cpp at `v2.3.0-sec.1` (the release set of
2026-11-07), by sha, never a branch. A build against ZephyrProtocol's original of the same
release (zephyr `v2.3.0`, zephyr-cpp `4a99272`) must work as well; the backports are the same
software plus Monero security fixes.

```sh
git clone https://github.com/loki-notfound/myzephyr-java.git
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

## Verifying a release

Release tags and `SHA256SUMS` are signed with the maintainer key:

```
31DA 39AB 43E0 CA67 F1F3  A661 BA2A FF22 DF8A 4A13
```

The public key is [`KEYS.asc`](KEYS.asc) in this repository and on keys.openpgp.org. To check a
release:

```sh
gpg --import KEYS.asc
git verify-tag v2.3.0-sec.2-java.3
gpg --verify SHA256SUMS.asc && sha256sum -c SHA256SUMS
```

`SHA256SUMS` covers every release file, `manifest.json` included. The Maven repository and the
site are the archive `site-<version>.tar.gz`, whose hash is in `SHA256SUMS`; Pages serves its
contents. The release flow builds every artifact twice in the same environment and the hashes must
match before the owner signs.

The core's tags are signed by zephyr-backports with its release key,
`FB39 94F0 997C A6F0 43D7  2491 9EFF 03F5 27D2 40A1` (`keys/zephyr-backports.asc` in the
zephyr-backports repository). The build scripts run `git verify-tag` on them when the tag is
present locally; without gpg or without that key in the keyring they print a warning and build
anyway, so that a build without the keys still works.

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
