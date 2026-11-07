# 1. Getting started

zephyr-java is a Java library around a native wallet core. The Java classes are plain Java 8
bytecode; the work is done by `libzephyr-java`, a native library that contains `zephyr-cpp` and
Zephyr's `wallet2`, linked statically, and exports nothing but its JNI entry points. A wallet
created with `ZephyrWalletFull` runs entirely inside your process: it talks to a Zephyr node over
the node's RPC interface, and keeps its keys and its cache in files or in byte arrays you store
yourself.

There are two artifacts with the same Java classes:

- **The JAR**, for desktop and server JVMs on Linux. It carries the native library for Linux
  `x86_64` and `arm64` as resources, so nothing else has to be installed.
- **The AAR**, for Android apps. It carries the native library for `arm64-v8a`, `armeabi-v7a` and
  `x86_64` under `jni/<abi>/`, and the R8 rules every app needs. [Chapter 6](06-android.md) is
  about Android.

The coordinates and the repository are in the [README](../../README.md#coordinates).

## Loading the native library

`ZephyrUtils` loads the native library in a static initialiser, the first time any class touches
it, and `ZephyrUtils.loadNativeLibrary()` does the same explicitly. It tries two places, in this
order:

1. `System.loadLibrary("zephyr-java")`, which searches `java.library.path`. On Android this is how
   the library is found: the AAR's `jni/<abi>/libzephyr-java.so` becomes part of the app's APK,
   and the app's class loader finds it there.
2. The JAR's resources: `/linux-x86_64/libzephyr-java.so` or `/linux-arm64/libzephyr-java.so`,
   chosen by `os.name` and `os.arch`. The library is copied to a temporary file, loaded with
   `System.load` and the temporary file is deleted again (a loaded library stays mapped).

If both fail, `loadNativeLibrary()` throws a `ZephyrError` that carries the first failure as a
suppressed exception, so you see both reasons. macOS and Windows libraries are not packaged yet;
on those systems the library has to be on `java.library.path`, which means building it yourself.
`ZephyrUtils.isNativeLibraryLoaded()` tells you whether the library is there.

A developer build from a checkout puts the library in `build/`; the Maven test runs point
`java.library.path` there (the property `native.library.path`). Your own program can do the same
with `-Djava.library.path=...`, which takes precedence over the copy in the JAR.

## Linux: the glibc floor

The release natives for Linux are built in a Debian 12 container. Everything is linked statically
except the C library, so the JAR needs glibc 2.36 or newer: Debian 12, Ubuntu 23.04 and later,
RHEL 10. On an older system the library fails to load with a message about a missing `GLIBC_2.xx`
version. A developer build made on a newer host has that host's glibc as its floor (on Debian 13,
2.38).

## Threads

Two rules hold for every wallet call:

- **Calls block.** `sync()`, `createTxs()`, `getTxs()` and the other wallet methods do their work
  on the calling thread and return when the core is done. A first sync of a restored wallet can
  take minutes, and building a transaction asks the node for decoy outputs. Never call them from
  a thread that must stay responsive, such as Android's main thread.
- **Listener callbacks come in on a native thread.** The core delivers notifications from its
  own notification thread, which the JNI bridge attaches to the JVM for the call. Your listener
  therefore runs on a thread you did not create. Hand the event over to your own thread
  (an executor, Android's main looper) and return quickly.

The second rule has a consequence that is easy to miss. During a sync, the core hands each new
block's notifications to its notification thread and **waits for them to finish** before it goes
on. A listener that does slow work, or that calls back into the same wallet and waits, stalls the
sync. Keep callbacks short and never call the wallet from inside one; queue the work instead.

```java
ExecutorService events = Executors.newSingleThreadExecutor();
wallet.addListener(new ZephyrWalletListener() {
  @Override
  public void onBalancesChanged(BigInteger balance, BigInteger unlockedBalance, String assetType) {
    events.submit(() -> showBalance(assetType, balance, unlockedBalance));  // return at once
  }
});
```

Background syncing (`startSyncing()`) runs on a thread of the core as well; the same rules apply
to the callbacks it triggers. [Chapter 2](02-wallets.md) has the details of syncing.

## A first program

The [README's quick start](../../README.md#quick-start) is a complete program: it opens or
creates a wallet, syncs it with a progress listener, prints the balance of every asset and, on
request, builds a transfer without relaying it. It is compiled with the test sources of this
repository (`src/test/java/examples/QuickStart.java`), so it always matches the API.

The rest of this guide:

- [2 Wallets](02-wallets.md): create, restore, open, connect, sync, listen, save and close.
- [3 Assets and balances](03-assets-and-balances.md): the assets and the balance API.
- [4 Sending and converting](04-sending-and-converting.md): transfers, conversions, fees.
- [5 Reserve info and circulating supply](05-reserve-and-supply.md).
- [6 Android](06-android.md).
- [7 Coming from monero-java](07-coming-from-monero-java.md).
- [8 Known limitations](08-known-limitations.md).
- [9 Building from source and verifying releases](09-building-and-verifying.md).

## Amounts

Every amount in the API is a `BigInteger` in atomic units: 10^12 atomic units are one coin, for
every asset (ZPH, ZSD, ZRS, ZYS alike). `ZephyrUtils.toAtomicUnits(double)` and
`ZephyrUtils.fromAtomicUnits(BigInteger)` convert, but they go through `double`; for user input
and display, `BigDecimal` is exact:

```java
BigInteger atomic = new BigDecimal("1.5").movePointRight(12).toBigIntegerExact();
String shown = new BigDecimal(atomic, 12).toPlainString();  // "1.500000000000"
```
