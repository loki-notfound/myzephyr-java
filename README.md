# zephyr-java

Java bindings for Zephyr's wallet core, for Android apps (AAR) and for desktop and server JVMs on
Linux (JAR).

The native core is `zephyr-cpp` over Zephyr's `wallet2`, built from the forks of
[zephyr-backports](https://github.com/zephyr-backports): ZephyrProtocol's Zephyr 2.3.0 plus Monero
security fixes. The Java API is derived from [monero-java](https://github.com/woodser/monero-java),
with Zephyr's assets, conversions and reserve information added. The repository is
`loki-notfound/myzephyr-java`; the library and its artifacts are named `zephyr-java`.

**Not affiliated with ZephyrProtocol.** An independent project.

## Status

This README describes release `v2.3.0-sec.2-java.5`, built from the zephyr-backports release set
of 2026-11-07: zephyr `v2.3.0-sec.2` and zephyr-cpp `v2.3.0-sec.1`. It fixes the closing and
listener defects of `java.4` and throws `ZephyrError` from every wallet call. It keeps secrets out
of the library's logging, moves the artifacts to Maven Central under `dev.lateo` and removes the
wallet RPC client. The [release notes](RELEASE-NOTES.md) list the breaking changes.

- **The JAR** carries the native libraries for Linux `x86_64` and `arm64` and needs glibc 2.36 or
  newer. The `arm64` library is built and checked but has not been run.
- **The AAR**, minSdk 24, carries the native library for `arm64-v8a`, `armeabi-v7a` and `x86_64`.
  Its `x86_64` library ran on the Android emulator on 2026-11-19 and 2026-11-20, on API 24, 35 and
  36, with 4 KB and 16 KB pages. In a minified app it ran eight scenarios of closing and process
  exit, two of them also in an app without R8. Under CheckJNI a debug app ran an offline wallet, a
  sync, `ZephyrDaemonRpc` and TLS with it. With an earlier bridge, apps crashed during
  background syncing on one of these images (API 35 with 16 KB pages), whose own processes crashed
  the same way. They did not crash on the other image with 16 KB pages (API 36). With the bridge of
  `java.5` none crashed on either, in 80 runs on each. The ARM libraries have not run on hardware.
- The natives carry no Trezor code and no protobuf: the Java API cannot reach a hardware device.

The guide's [What was tested](docs/guide/13-known-limitations.md#what-was-tested) has the details,
and what was not tested.

## What it does

`ZephyrWalletFull` is a wallet that runs in your process. It creates, restores and opens wallets,
syncs against a Zephyr node, sends transfers, converts between ZPH, ZSD, ZRS and ZYS, and reports
the state of the reserve. During the work on the releases up to `java.4` (2026-11-06 to
2026-11-10), zephyr-java ran on mainnet with real funds. It created, opened and synced wallets,
showed balances per asset, sent transfers, converted ZPH to ZRS and swept accounts. `java.5` was
tested read-only: it has not sent, converted or swept.

Outside its scope, or not ready:

- native libraries for macOS and Windows, which are not packaged;
- a wallet RPC client: `ZephyrWalletRpc` was removed in `java.5`. `ZephyrDaemonRpc` reads a node,
  and beyond the node's information and block headers it is not tested;
- testnet and stagenet, which never reach the rules of the current assets;
- conversions other than ZPH → ZRS, which have not been run on mainnet;
- the inherited areas listed in
  [What zephyr-java does and does not do](docs/guide/01-getting-started.md#what-zephyr-java-does-and-does-not-do),
  not tested against Zephyr.

## Coordinates

Releases are published on Maven Central under the groupId `dev.lateo`, and as GitHub releases with
the build manifest and the signed `SHA256SUMS`. Maven uses Maven Central by default, and an
Android Studio project lists `mavenCentral()` in its `settings.gradle.kts`, so a project adds the
dependency and no repository line. The project's site,
`https://java-docs.lateo.dev`, carries the user guide and the Javadoc
([Documentation](#documentation)). Releases up to `2.3.0-sec.2-java.4` were published under
`io.github.loki-notfound` in a Maven repository on the project's GitHub Pages. That repository is
no longer served; those releases remain as GitHub releases. A build that still resolves one of
them from that repository stops resolving. Move to `java.5`, or use the older release's JAR or AAR
as a file dependency with its three runtime dependencies
([Maven Central](docs/guide/14-building-and-verifying.md#maven-central)).

Android (Gradle, Kotlin DSL). The AAR has its own artifactId, `zephyr-java-android`, and brings
its POM's dependencies (Jackson and the Apache HTTP client) along. The dependency goes into the app
module's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("dev.lateo:zephyr-java-android:2.3.0-sec.2-java.5")
}
```

Every Android app that uses the AAR also needs this line in its `android { }` block:

```kotlin
packaging { resources.excludes += "/META-INF/DEPENDENCIES" }
```

Three of the Apache HTTP client's jars carry a file `META-INF/DEPENDENCIES`, and the app's resource
merge fails on the duplicates. The app also declares the `INTERNET` permission. The guide's
[Android](docs/guide/10-android.md) chapter has the Groovy and version catalog forms, the desugaring the
RPC classes need on API 24 and 25, ABIs and sizes, backup, logs and a first test.

Desktop and server (Maven), the JAR with the Linux natives:

```xml
<dependency>
  <groupId>dev.lateo</groupId>
  <artifactId>zephyr-java</artifactId>
  <version>2.3.0-sec.2-java.5</version>
</dependency>
```

The Gradle form for a JVM project is in
[Adding the dependency](docs/guide/01-getting-started.md#adding-the-dependency).

## Quick start

`src/test/java/examples/QuickStart.java` opens or creates a mainnet wallet and syncs it with a
listener that prints the progress. It prints the balance of the four assets. With `--send` it
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

      // sync; the listener is called once per block fetched
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

Every send names a source and a destination currency. A plain transfer uses the same asset for
both, a conversion two different ones. [Sending](docs/guide/07-sending.md) and
[Conversions and the reserve](docs/guide/08-conversions-and-the-reserve.md) explain the pairs and
the fees.

## Known limitations

The guide's [Known limitations](docs/guide/13-known-limitations.md) lists every limit of
`2.3.0-sec.2-java.5` with its status, and what `java.5` fixed. Three matter most for an app:

- **Closing and callbacks.** `close()` waits for the calls in flight and the core's sync thread,
  without a time limit, so call it off an app's main thread. Close every wallet before the
  process ends: a process can hang in its exit while background syncing refreshes. A listener
  callback cannot sync, relay, import, save or close the wallet. Callbacks of two threads can
  overlap
  ([Closing a wallet that syncs](docs/guide/04-threads-and-listeners.md#closing-a-wallet-that-syncs),
  [What a callback may call](docs/guide/04-threads-and-listeners.md#what-a-callback-may-call)).
  Up to `java.4` a `close()` during a sync could crash the process.
- **What the logs hold.** The core's log, once switched on, records the primary address, amounts
  and transaction hashes. The library's own logging keeps secrets out from `java.5` on; `java.4`
  logged passwords, mnemonics and transaction keys at some settings
  ([Logging safely](docs/guide/09-errors.md#logging-safely),
  [The native log](docs/guide/09-errors.md#the-native-log)).
- **Amounts and assets the core does not report.** `checkTxKey` returns amount 0 for every
  current transaction and reports `good` whenever it does not throw, so it cannot confirm a
  payment; `getOutputs()` reports no asset. Read what was received from the wallet's history and
  the asset with `getOutputAssetType`
  ([checkTxKey returns amount 0](docs/guide/06-history.md#checktxkey-returns-amount-0),
  [getOutputs() reports no asset](docs/guide/06-history.md#getoutputs-reports-no-asset)).

## Documentation

- [The user guide on the project's site](https://java-docs.lateo.dev/guide/).
- [The user guide as Markdown](docs/guide/README.md) in this repository, the same text.
- [The Javadoc of the current release](https://java-docs.lateo.dev/javadoc/).
  Every release also ships it as a `-javadoc.jar`; `mvn javadoc:javadoc` builds it from a checkout.
- [The releases on GitHub](https://github.com/loki-notfound/myzephyr-java/releases), and the
  [release notes](RELEASE-NOTES.md) in this repository.

## Security and trust model

The wallet runs in your process and keeps its keys there. The node it connects to sees where the
connections come from, when the wallet syncs and every transaction it sends. The core treats every
node as trusted ([A remote node](docs/guide/03-connecting-to-a-node.md#a-remote-node)). On Android
the native wallet trusts the root certificates shipped in the library by default, and there is no
certificate pinning ([TLS to a node](docs/guide/03-connecting-to-a-node.md#tls-to-a-node)).

Vulnerabilities in the Zephyr core (zephyr, zephyr-cpp, `wallet2`) go to the ZephyrProtocol team.
Vulnerabilities in zephyr-java itself (the Java code, the JNI bridge, the build and the release
artifacts) go to `loki@lateo.dev`.

## Building from source

The native core is built only from the pinned commits in `bin/pins.env`, never from a branch:
zephyr `v2.3.0-sec.2` and zephyr-cpp `v2.3.0-sec.1` of zephyr-backports. A build against
ZephyrProtocol's original of the same release must work as well
([The pins](docs/guide/14-building-and-verifying.md#the-pins)).

```sh
git clone https://github.com/loki-notfound/myzephyr-java.git
cd myzephyr-java
git submodule update --init external/zephyr-cpp
```

- `bin/build_libzephyr_java.sh`: the quick developer build against the host's shared libraries,
  for `mvn test`. The result runs only on hosts like the one that built it.
- `bin/build_linux_container.sh`: the Linux release natives (`x86_64`, `arm64`) in the pinned
  `debian:12` image, with podman or docker.
- `bin/build_android.sh`: the Android natives per ABI with zephyr's `contrib/depends` and NDK r18b.
- `bin/build_release.sh`: everything, into `dist/<version>/`: the JAR, the AAR, sources, Javadoc,
  debug symbols, `manifest.json` and `SHA256SUMS`. It also writes the Maven Central bundle, which
  the maintainer signs with `bin/sign_central_bundle.sh`.

The guide's [Building and verifying](docs/guide/14-building-and-verifying.md) has the patches,
the manifest and the steps to reproduce a release.

## Verifying a release

Release tags and `SHA256SUMS` are signed with the maintainer key:

```text
31DA 39AB 43E0 CA67 F1F3  A661 BA2A FF22 DF8A 4A13
```

The public key is [`KEYS.asc`](KEYS.asc) in this repository and on keys.openpgp.org. To check a
release:

```sh
gpg --import KEYS.asc
git verify-tag v2.3.0-sec.2-java.5
gpg --verify SHA256SUMS.asc SHA256SUMS
sha256sum -c SHA256SUMS
```

Run `git verify-tag` in a clone of this repository, and the last two commands in the directory of
the release files. `SHA256SUMS` covers every release file, `manifest.json` and the site archive
included. The same key signs every file on Maven Central (its `.asc`). Each of those files is also
a release file under `SHA256SUMS`, or listed in `manifest.json`. The guide's
[Signatures](docs/guide/14-building-and-verifying.md#signatures) also names the key that signs the
core's tags.

## Versioning

A release is tagged `v<zephyr version>-sec.<N>-java.<M>`, for example `v2.3.0-sec.2-java.1`; the
Maven version is the same without the `v`. `sec.<N>` is the number of **zephyr's** backports tag.
`java.<M>` counts the zephyr-java releases on that core and starts again at 1 for each new core
tag. The zephyr-cpp tag is named in the tag message, in `manifest.json` and in this README. The
version does not encode API compatibility: changes to the Java API are listed under "Breaking
changes" in the release notes. Tags are annotated and signed, and never moved or reused.

## Credits and licence

Derived from monero-java by woodser (MIT), whose copyright notices are kept in the source files.
zephyr-java is MIT licensed as well; see [`LICENCE.txt`](LICENCE.txt).

Third-party data: the root certificates shipped in the library (`zephyr/common/cacert.pem`) are
Mozilla's CA list in curl's PEM form, from [curl's CA extract](https://curl.se/docs/caextract.html),
under the [Mozilla Public License 2.0](https://www.mozilla.org/MPL/2.0/). From
`2.3.0-sec.2-java.5` on, the POMs of the JAR and the AAR name both licences.

## Donations

If zephyr-java is useful to you, donations are welcome at this Zephyr address:

```text
ZEPHs8MmrN556ieVvv9YUZf3J5pW2uN1z5S88mb9DksG9bcEcqVGg9XbuQc6Q9e9Ka6bjMHYgaSidWfL1r1DGLyngj8NHT3Q6gp
```
