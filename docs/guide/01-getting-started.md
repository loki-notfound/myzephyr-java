# 1. Getting started

zephyr-java is a Java library around a native wallet core. The Java classes are plain Java 8
bytecode. The work is done by `libzephyr-java`, a native library that contains `zephyr-cpp` and
Zephyr's `wallet2`, linked statically, and exports nothing but its JNI entry points.

A wallet created with `ZephyrWalletFull` runs entirely inside your process. It talks to a Zephyr
node over the node's RPC interface. It keeps its keys and its cache in files, or in byte arrays you
store yourself.

## What zephyr-java does and does not do

The full wallet, `ZephyrWalletFull`, is the complete API. With real funds on mainnet it has
created, opened and synced wallets, shown balances per asset, sent transfers, converted ZPH to ZRS
and swept accounts. It also reports the state of the reserve
([Conversions and the reserve](08-conversions-and-the-reserve.md)).

Outside its scope, or not ready:

- **macOS and Windows.** Their native libraries are not packaged; on those systems you build the
  library yourself ([The native library](#the-native-library)).
- **The RPC classes.** `ZephyrWalletRpc` and `ZephyrDaemonRpc` are a first pass; `ZephyrWalletRpc`
  serves the base coin only ([The RPC classes](11-the-rpc-classes.md)).
- **Testnet and stagenet.** They never reach the rules of the current assets
  ([Network types](02-wallets.md#network-types)).
- **Conversions other than ZPH → ZRS.** They use the same code path but have not been run on
  mainnet ([What was tested](08-conversions-and-the-reserve.md#what-was-tested)).

The API inherited from monero-java is larger than the part that was run against Zephyr. Each of
these areas is inherited from monero-java; not tested against Zephyr:

- multisig wallets (`prepareMultisig` and the calls after it);
- mining (`startMining` on the wallet and on `ZephyrDaemonRpc`);
- the export and import of key images;
- the address book;
- `ZephyrConnectionManager`;
- the proxy of `setProxyUri`;
- seed offsets and mnemonic languages (`setSeedOffset`, `setLanguage`);
- creating accounts and subaddresses and setting their labels;
- several destinations in one transaction, and the priority, fee, unlock height, payment ID and
  split settings of `ZephyrTxConfig`;
- transaction proofs, spend proofs, reserve proofs and message signatures;
- the calls of `ZephyrDaemonRpc` other than the node's information and block headers.

[Known limitations](13-known-limitations.md) lists them again with the limits that were found.

## Requirements

| What | The JAR | The AAR |
|---|---|---|
| Runs on | Linux `x86_64` and `arm64` JVMs | Android 7.0 (API 24) and newer |
| Java | classes compiled for Java 8 | the same classes |
| Native library | in the JAR; needs glibc 2.36 or newer | in the AAR for `arm64-v8a`, `armeabi-v7a` and `x86_64` |
| Dependencies it brings | Jackson 2.13.5 (`jackson-core`, `jackson-databind`) and `httpclient5` 5.2.1 | the same |
| A node | a Zephyr mainnet node (`zephyrd`) to sync and send | the same |

The `arm64` library for Linux is built and checked but has not been run
([What was tested](13-known-limitations.md#what-was-tested)).

## Adding the dependency

There are two artifacts with the same Java classes:

- **The JAR**, `io.github.loki-notfound:zephyr-java`, for desktop and server JVMs on Linux. It
  carries the native library for Linux `x86_64` and `arm64` as resources, so nothing else has to be
  installed.
- **The AAR**, `io.github.loki-notfound:zephyr-java-android`, for Android apps. It carries the
  native library for `arm64-v8a`, `armeabi-v7a` and `x86_64` under `jni/<abi>/`, and the R8 rules
  every app needs. [Android](10-android.md) has its Gradle lines.

Both come from a Maven repository on the project's GitHub Pages. For a JVM project with Gradle
(Kotlin DSL):

```kotlin
repositories {
    mavenCentral()
    maven { url = uri("https://loki-notfound.github.io/myzephyr-java/maven") }
}

dependencies {
    implementation("io.github.loki-notfound:zephyr-java:2.3.0-sec.2-java.4")
}
```

The Maven form and the repository are in the [README's coordinates](../../README.md#coordinates).

## The native library

`ZephyrUtils` loads the native library in a static initialiser, the first time any class touches
it. `ZephyrUtils.loadNativeLibrary()` does the same explicitly. It tries two places, in this order:

1. `System.loadLibrary("zephyr-java")`, which searches `java.library.path`. On Android this is how
   the library is found: the AAR's `jni/<abi>/libzephyr-java.so` becomes part of the app's APK,
   and the app's class loader finds it there.
2. The JAR's resources: `/linux-x86_64/libzephyr-java.so` or `/linux-arm64/libzephyr-java.so`,
   chosen by `os.name` and `os.arch`. The library is copied to a temporary file, loaded with
   `System.load` and the temporary file is deleted again (a loaded library stays mapped).

If both fail, `loadNativeLibrary()` throws a `ZephyrError`. It carries the first failure as a
suppressed exception, so you see both reasons. `ZephyrUtils.isNativeLibraryLoaded()` tells you
whether the library is there, and `ZephyrUtils.getVersion()` returns the library's version.

macOS and Windows libraries are not packaged in `2.3.0-sec.2-java.4`. On those systems the
library has to be on `java.library.path`, which means building it yourself.

A developer build from a checkout puts the library in `build/`. The Maven test runs point
`java.library.path` there (the property `native.library.path`). Your own program can do the same
with `-Djava.library.path=...`, which takes precedence over the copy in the JAR.

### Linux: the glibc floor

The release natives for Linux are built in a Debian 12 container. Everything is linked statically
except the C library, so the JAR needs glibc 2.36 or newer: Debian 12, Ubuntu 23.04 and later,
RHEL 10. On an older system the library fails to load with a message about a missing `GLIBC_2.xx`
version. A developer build made on a newer host has that host's glibc as its floor (on Debian 13,
2.38).

## The classes you meet first

| Class | Package | What it is |
|---|---|---|
| `ZephyrWalletFull` | `zephyr.wallet` | the wallet that runs in your process |
| `ZephyrWalletConfig` | `zephyr.wallet.model` | the settings to create or open a wallet |
| `ZephyrNetworkType` | `zephyr.daemon.model` | `MAINNET`, and the two test networks |
| `ZephyrRpcConnection` | `zephyr.common` | a node's URI with optional login |
| `ZephyrWalletListener` | `zephyr.wallet.model` | the callbacks for sync progress, blocks, balances and outputs |
| `ZephyrAssets`, `ZephyrBalance` | `zephyr.wallet.model` | the asset names and a balance per asset |
| `ZephyrTxConfig`, `ZephyrTxWallet` | `zephyr.wallet.model` | what to send, and the transaction that comes back |
| `ZephyrError` | `zephyr.common` | the library's exception; some calls let the core's `java.lang.Exception` through instead |
| `ZephyrUtils` | `zephyr.common` | the native library, address checks, amount helpers |
| `ZephyrTls` | `zephyr.common` | the root certificates the native wallet trusts |

Wallet calls block the calling thread, and listener callbacks arrive on threads you did not
create; [Threads and listeners](04-threads-and-listeners.md) has the rules. Amounts are
`BigInteger` atomic units, 10^12 per coin ([Amounts](05-assets-and-balances.md#amounts)).

## A first program

The [README's quick start](../../README.md#quick-start) is a complete program. It opens or creates
a wallet, syncs it with a progress listener, prints the balance of every asset and, on request,
builds a transfer without relaying it. It is compiled with the test sources of this repository
(`src/test/java/examples/QuickStart.java`), so it always matches the API.

Before you build the wallet into an app, check that the library loads and works on your machines
and devices. `src/test/java/examples/GuideFirstProgram.java` is plain Java, compiled with this
project's tests, and needs neither a node nor storage:

```java examples/GuideFirstProgram.java
// loads the native library, creates an offline wallet in memory and returns its primary address
public static String smokeTest() {
  ZephyrUtils.loadNativeLibrary();
  ZephyrWalletFull wallet = ZephyrWalletFull.createWallet(new ZephyrWalletConfig()
      .setNetworkType(ZephyrNetworkType.MAINNET)
      .setPassword("smoke-test"));  // no path and no node: nothing touches storage or the network
  try {
    return wallet.getPrimaryAddress();
  } finally {
    wallet.close();
  }
}
```

On the desktop, the class's `main` prints the version and the address:

```java examples/GuideFirstProgram.java
public static void main(String[] args) {
  System.out.println("zephyr-java " + ZephyrUtils.getVersion());
  System.out.println("primary address: " + smokeTest());
}
```

A mainnet address (it starts with `ZEPHYR`) means that the library loaded and the core created a
wallet. On Android, call `smokeTest()` off the main thread
([A first test on a device](10-android.md#a-first-test-on-a-device)).
