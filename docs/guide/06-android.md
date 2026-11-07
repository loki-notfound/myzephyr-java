# 6. Android

The AAR is the same library as the JAR, packaged for Android: the Java classes, the native library
for three ABIs and the R8 rules an app needs. This chapter covers what is different on Android:
the dependency, the devices it runs on, storage, lifecycle, threads, and TLS to a node.

## The dependency

```kotlin
repositories {
    maven { url = uri("https://loki-notfound.github.io/zephyr-java/maven") }
}

android {
    packaging { resources.excludes += "/META-INF/DEPENDENCIES" }
}

dependencies {
    implementation("io.github.loki-notfound:zephyr-java-android:2.3.0-sec.2-java.1")
}
```

The repository goes live with the first release. The AAR is published as `zephyr-java-android`
(the JAR for the JVM is `zephyr-java`) and brings three dependencies: Jackson
(`jackson-core`, `jackson-databind`, 2.13.5) and the Apache HTTP client 5 (`httpclient5` 5.2.1,
used by the RPC classes). It brings no Kotlin standard library, no Bouncy Castle and no Apache
commons-codec.

**The packaging line is required.** `httpclient5`, `httpcore5` and `httpcore5-h2` each carry a
file `META-INF/DEPENDENCIES`, and without the exclude the app's build stops in
`mergeDebugJavaResource` with "3 files found with path 'META-INF/DEPENDENCIES'". The file is Maven
metadata; nothing reads it at run time.

**Jackson.** The library pins Jackson 2.13.5 because 2.13 is the last Jackson line that runs on
Android below API 26 (earlier 2.13 releases call `Method.getParameterCount()`, which API 24 and
25 lack). Gradle resolves the highest Jackson that anything in the app requests. If your app or
another library brings Jackson 2.14 or newer, your app needs minSdk 26 for Jackson's sake.
zephyr-java itself was tested on Jackson 2.21.7 on the JVM (the coexistence test); on Android it
has only been built with 2.13.5.

**jeromq** is a compile-only dependency. Only `ZephyrWalletRpc`'s ZMQ listener uses it; an app that
uses that listener adds `org.zeromq:jeromq` itself. The shipped R8 rules carry a `-dontwarn` for
it, so an app without jeromq builds cleanly.

## Devices

- **minSdk 24** (Android 7.0).
- **ABIs:** `arm64-v8a`, `armeabi-v7a` and `x86_64`. There is no 32-bit `x86` library. The
  `armeabi-v7a` library is built with zephyr's own `-mfpu=vfpv4`, so it needs a CPU with VFPv4
  (Cortex-A8 and A9 cores lack it); every Android 7 device supports it.
- **16 KB pages:** the libraries are linked for 16 KB page alignment, which Google Play requires for
  apps that target Android 15 and later. Keep them uncompressed in the APK
  (`packaging { jniLibs.useLegacyPackaging = false }`); an app built against the AAR with AGP
  9.5.0 passes `zipalign -c -P 16`, with the three libraries stored.
- **Size:** each ABI's library is about 28 to 35 MB before stripping. Every ABI you ship adds one.

The libraries have been built and checked on this project's build host, but **not yet run on a
device or an emulator**: the build host has no hardware virtualisation.

## The shipped R8 rules

The AAR's `proguard.txt` is applied to every app that uses it. It keeps:

- every class with `native` methods and those methods' names, because the native library binds
  its exports by class and method name;
- `ZephyrWalletFull$WalletJniListener` with all members, because the native side looks it up by
  name and calls its five callbacks by name and signature;
- the model packages `zephyr.wallet.model`, `zephyr.daemon.model` and `zephyr.common`, because
  Jackson maps the native JSON onto them by reflection;
- annotations and generic signatures, which Jackson reads at run time.

Your own listener classes need no rule: the native side calls the library's listener, which calls
yours through ordinary Java.

## Storage

Android apps usually keep their data in the app's private storage or in their own database. Both
work:

- **Files:** give `createWallet` a path inside `context.getFilesDir()`. The wallet writes
  `<name>.keys` and `<name>` there on `save()`.
- **Bytes:** create the wallet without a path, take `wallet.getData()` (keys and cache) and store
  the two arrays yourself; open later with `ZephyrWalletFull.openWalletData(...)`. This suits an
  encrypted database. The keys array is encrypted with the wallet password; the cache array holds
  the synced history.

With bytes, store again after each sync and after each send, since there is no `save()` to a file.
`changePassword` does not work on a wallet opened from bytes ([chapter 8](08-known-limitations.md)).

## Lifecycle and threads

A wallet is expensive to open and must be closed exactly once, so it belongs to something that
outlives an Activity: a ViewModel, a bound service, or an application-wide holder. Close it (after
storing it) when that owner ends.

Two rules from [chapter 1](01-getting-started.md#threads) matter more on Android:

- **No wallet call on the main thread.** Every call blocks until the core is done; a sync or a send
  on the main thread freezes the app. Use an executor or a coroutine dispatcher for I/O.
- **Callbacks arrive on a native thread.** Post to the main thread before you touch views:

```java
Handler main = new Handler(Looper.getMainLooper());
wallet.addListener(new ZephyrWalletListener() {
  @Override
  public void onBalancesChanged(BigInteger balance, BigInteger unlocked, String assetType) {
    main.post(() -> viewModel.setBalance(assetType, balance, unlocked));
  }
});
```

While the app is in the background, Android may stop it at any time. Store the wallet when the app
goes to the background (`onStop`), and stop background syncing (`stopSyncing()`) if you do not
need it there.

## TLS to a node

The native wallet makes its own connections to the node, with the OpenSSL inside the native
library. It does **not** use Android's certificate store, and the Java API has no option for a CA
file or a certificate fingerprint for the native wallet (`SslOptions` exists only for
`ZephyrWalletRpc`). With an `https://` URI the core verifies the node's certificate against
OpenSSL's default store and checks the host name.

That default store is empty on a device: the library's compiled-in OpenSSL directory is `/etc/ssl`,
a Linux location. So with an `https://` node:

- **Without further setup the connection fails.** The Java side only sees "Wallet is not connected
  to daemon"; the native log says "SSL certificate is not in the allowed list, connection dropped".
- **The fix is the environment.** OpenSSL reads `SSL_CERT_FILE` (a PEM bundle) and `SSL_CERT_DIR`
  (a hashed directory) from the process environment when the TLS context is set up. Set one before
  the first wallet call, for example with a CA bundle the app ships and copies to its files
  directory:

```java
File bundle = new File(context.getFilesDir(), "cacert.pem");   // copied from the app's assets
android.system.Os.setenv("SSL_CERT_FILE", bundle.getAbsolutePath(), true);
```

- Pointing `SSL_CERT_DIR` at Android's own `/system/etc/security/cacerts` is the obvious candidate,
  but it is **untested**; whether OpenSSL accepts that directory's layout is not known yet. Test it
  on your devices before you rely on it.
- **Plain `http://` to a node you trust**, for example your own node on your own network, needs no
  certificates at all.

On the JVM the same applies: the Linux release library looks in `/etc/ssl`, and `SSL_CERT_FILE` or
`SSL_CERT_DIR` override it. The Java RPC classes are different: they use the JVM's trust store.
