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
    implementation("io.github.loki-notfound:zephyr-java-android:2.3.0-sec.1-java.1")
}
```

The repository goes live with the first release. The AAR is published as `zephyr-java-android`
(the JAR for the JVM is `zephyr-java`) and brings three dependencies: Jackson
(`jackson-core`, `jackson-databind`, 2.13.5) and the Apache HTTP client 5 (`httpclient5` 5.2.1,
used by the RPC classes). It brings no Kotlin standard library, no Bouncy Castle and no Apache
commons-codec.
<!-- README "Coordinates"; android/gradle.properties ARTIFACT_ID (QUESTIONS R12-3 answer); android/build.gradle.kts (api jackson-core, jackson-databind 2.13.5, httpclient5 5.2.1; compileOnly jeromq); QUESTIONS R10-2 answer (android.builtInKotlin=false, POM and module metadata with exactly three dependencies); register D24 -->

**The packaging line is required.** `httpclient5`, `httpcore5` and `httpcore5-h2` each carry a
file `META-INF/DEPENDENCIES`, and without the exclude the app's build stops in
`mergeDebugJavaResource` with "3 files found with path 'META-INF/DEPENDENCIES'". The file is Maven
metadata; nothing reads it at run time.
<!-- QUESTIONS R10-5 item 1 (verbatim error) and answer (a); evidence/r13/consumer-android.app.build.gradle.kts packaging block -->

**Jackson.** The library pins Jackson 2.13.5 because 2.13 is the last Jackson line that runs on
Android below API 26 (earlier 2.13 releases call `Method.getParameterCount()`, which API 24 and
25 lack). Gradle resolves the highest Jackson that anything in the app requests. If your app or
another library brings Jackson 2.14 or newer, your app needs minSdk 26 for Jackson's sake.
zephyr-java itself was tested on Jackson 2.21.7 on the JVM (the coexistence test); on Android it
has only been built with 2.13.5.
<!-- RELEASES §6 (jackson-databind #3412; 2.13 for Android 24+, 2.14 for 26+; "Gradle resolves the highest requested Jackson in an app"); QUESTIONS R10-5 item 3 answer and R10-6 (zephyr-java runs on Jackson 2.21.7, JVM coexistence profile) -->

**jeromq** is a compile-only dependency. Only `ZephyrWalletRpc`'s ZMQ listener uses it; an app that
uses that listener adds `org.zeromq:jeromq` itself. The shipped R8 rules carry a `-dontwarn` for
it, so an app without jeromq builds cleanly.
<!-- QUESTIONS R10-2 answer (b); android/consumer-rules.pro (-dontwarn org.zeromq.**); ZephyrWalletRpc.java WalletRpcZmqListener Javadoc -->

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
<!-- RELEASES §6 (minSdk 24, final); RELEASES §7 R9 outcome (-mfpu=vfpv4, "every Android 7 device supports"); evidence/r9/android-r9.md:163-165 (VFPv4 from zephyr's CMakeLists; Cortex-A8/A9 without VFPv4); RELEASES §3 (16 KB page size, Google Play requirement for apps targeting Android 15+); PLAN §5 R9 result (sizes 31.7, 27.6, 34.5 MB before stripping) and R10 result (APK passes zipalign -c -P 16, .so stored and 16 KB aligned); evidence/r13/release-candidate-r13.md (the consumer app check: AGP 9.5.0, zipalign -c -P 16, the three libraries stored) -->

The libraries have been built and checked on this project's build host, but **not yet run on a
device or an emulator**: the build host has no hardware virtualisation.
<!-- PLAN §5 R10 ("The VM has no KVM") -->

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
<!-- android/consumer-rules.pro (all four rule groups, with their comments); ZephyrWalletFull.java WalletJniListener delegating to the registered ZephyrWalletListenerI -->

## Storage

Android apps usually keep their data in the app's private storage or in their own database. Both
work:

- **Files:** give `createWallet` a path inside `context.getFilesDir()`. The wallet writes
  `<name>.keys` and `<name>` there on `save()`.
- **Bytes:** create the wallet without a path, take `wallet.getData()` (keys and cache) and store
  the two arrays yourself; open later with `ZephyrWalletFull.openWalletData(...)`. This suits an
  encrypted database. The keys array is encrypted with the wallet password; the cache array holds
  the synced history.
<!-- chapter 2 "Opening from bytes"; ZephyrWalletFull.getData (getKeysFileBufferJni(password, ...), getCacheFileBufferJni) -->

With bytes, store again after each sync and after each send, since there is no `save()` to a file.
`changePassword` does not work on a wallet opened from bytes ([chapter 8](08-known-limitations.md)).

## Lifecycle and threads

A wallet is expensive to open and must be closed exactly once, so it belongs to something that
outlives an Activity: a ViewModel, a bound service, or an application-wide holder. Close it (after
storing it) when that owner ends.
<!-- ZephyrWallet.close / close(boolean) -->

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
<!-- PLAN §5 R10 (calls block; callbacks on a native thread); zephyr_jni_bridge.cpp:171 (AttachCurrentThread) -->

While the app is in the background, Android may stop it at any time. Store the wallet when the app
goes to the background (`onStop`), and stop background syncing (`stopSyncing()`) if you do not
need it there.

## TLS to a node

The native wallet makes its own connections to the node, with the OpenSSL inside the native
library. It does **not** use Android's certificate store, and the Java API has no option for a CA
file or a certificate fingerprint for the native wallet (`SslOptions` exists only for
`ZephyrWalletRpc`). With an `https://` URI the core verifies the node's certificate against
OpenSSL's default store and checks the host name.
<!-- evidence/r10/packaging-r10.md stage E (monero_wallet_full.cpp:1330-1349 set_daemon_connection; net_ssl.h:87-93 ssl_options_t(ssl_support_t) leaves fingerprints and ca_path empty, verification = system_ca; net_ssl.cpp:339-345 set_default_verify_paths, :525-541 RFC 2818 host check; "zephyr-cpp exposes neither a CA file nor fingerprints"); ZephyrWalletRpc.setDaemonConnection(connection, isTrusted, SslOptions) -->

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
<!-- PLAN §5 R10 (the native library does not use Android's CA store) and R11 (OPENSSLDIR, R12 to test /system/etc/security/cacerts on the owner's device); patches/depends/0004-openssl-openssldir.patch (OPENSSLDIR /etc/ssl); evidence/r10/packaging-r10.md stage E (OpenSSL 3.0.13 by_file.c:57-65 and by_dir.c:91-97 read SSL_CERT_FILE / SSL_CERT_DIR; cases a: https with SSL_CERT_FILE succeeds, b: without it fails with the quoted log lines, c: http succeeds); order R12 stage C item 6 (the candidate is untested) -->

On the JVM the same applies: the Linux release library looks in `/etc/ssl`, and `SSL_CERT_FILE` or
`SSL_CERT_DIR` override it. The Java RPC classes are different: they use the JVM's trust store.
<!-- patches/depends/0004 (OPENSSLDIR /etc/ssl for every depends host); evidence/r10/packaging-r10.md (SSLContexts.createSystemDefault() in ZephyrRpcConnection.java:616) -->
