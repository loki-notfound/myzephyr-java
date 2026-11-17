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
  `x86_64` under `jni/<abi>/`, and the R8 rules every app needs. [Chapter 10](10-android.md) is
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

## A first program

The [README's quick start](../../README.md#quick-start) is a complete program: it opens or
creates a wallet, syncs it with a progress listener, prints the balance of every asset and, on
request, builds a transfer without relaying it. It is compiled with the test sources of this
repository (`src/test/java/examples/QuickStart.java`), so it always matches the API.

Before you build the wallet into the app, check that the library loads and works on your devices.
`src/test/java/examples/GuideFirstProgram.java` is plain Java, compiled with this project's tests:

```java
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
