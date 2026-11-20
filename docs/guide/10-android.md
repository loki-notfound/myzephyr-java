# 10. Android

The AAR is the same library as the JAR, packaged for Android. It holds the Java classes, the native
library for three ABIs and the R8 rules an app needs. This chapter covers what is different on
Android: the dependency, devices, the RPC classes below API 26 and the network. It then covers
storage, backup, lifecycle, logs, a first test and the R8 rules. TLS to a node, with Android's
default trust, is in [TLS to a node](03-connecting-to-a-node.md#tls-to-a-node).

## The dependency

The AAR is on Maven Central. A project created by Android Studio already lists `mavenCentral()`
where it declares its repositories (`settings.gradle.kts`), so no repository line is added. The
dependency and the packaging line go into the app module's `build.gradle.kts`:

```kotlin
android {
    packaging { resources.excludes += "/META-INF/DEPENDENCIES" }
}

dependencies {
    implementation("dev.lateo:zephyr-java-android:2.3.0-sec.2-java.5")
}
```

> [!WARNING]
> **The packaging line is required.** `httpclient5`, `httpcore5` and `httpcore5-h2` each carry a
> file `META-INF/DEPENDENCIES`. Without the exclude the app's build stops in
> `mergeDebugJavaResource` with "3 files found with path 'META-INF/DEPENDENCIES'". The file is
> Maven metadata; nothing reads it at run time.

A project whose repositories do not include `mavenCentral()` adds it where the others are
declared. With `repositoriesMode` set to `FAIL_ON_PROJECT_REPOS`, as in Android Studio's template,
that is the settings file. A `repositories { }` block in a module's build file then stops the
build.

The same in the Groovy DSL (`app/build.gradle`):

```groovy
android {
    packaging { resources.excludes += '/META-INF/DEPENDENCIES' }
}

dependencies {
    implementation 'dev.lateo:zephyr-java-android:2.3.0-sec.2-java.5'
}
```

With a version catalog, `gradle/libs.versions.toml` gets the entry and the module uses
`implementation(libs.zephyr.java.android)` (Groovy: `implementation libs.zephyr.java.android`):

```toml
[versions]
zephyrJava = "2.3.0-sec.2-java.5"

[libraries]
zephyr-java-android = { module = "dev.lateo:zephyr-java-android", version.ref = "zephyrJava" }
```

The AAR is published as `zephyr-java-android` (the JAR for the JVM is `zephyr-java`). It brings
three dependencies: Jackson (`jackson-core`, `jackson-databind`, 2.13.5) and the Apache HTTP client
5 (`httpclient5` 5.2.1, used by the RPC classes). It brings no Kotlin standard library, no Bouncy
Castle, no Apache commons-codec and, from `2.3.0-sec.2-java.5` on, no jeromq
([Dependencies](11-the-rpc-classes.md#dependencies)).

### Jackson on API 24 and 25

> [!WARNING]
> **A newer Jackson breaks API 24 and 25.** The library pins Jackson 2.13.5, the last Jackson line
> that runs on Android below API 26. Gradle does not report a conflict when your app or another
> library asks for Jackson 2.14 or newer. The app then fails at run time on API 24 and 25.

Within the 2.13 line, releases before 2.13.5 call `Method.getParameterCount()`, which Android has
only from API 26. Gradle resolves
the highest version for the whole app, zephyr-java included, and the build succeeds. On API 26 and
newer that is fine: zephyr-java was tested on Jackson 2.21.7 on the JVM, in the coexistence test.
Check what the app really gets:

```sh
./gradlew :app:dependencies --configuration releaseRuntimeClasspath | grep jackson
```

A line such as `jackson-databind:2.13.5 -> 2.21.7` means that another dependency lifted Jackson.
Then minSdk 24 and that dependency do not go together. Raise minSdk to 26, or use a version of the
other library that stays on Jackson 2.13. On Android zephyr-java has run with 2.13.5 only.

## Devices

- **minSdk 24** (Android 7.0). The native wallet, `ZephyrWalletFull`, runs from API 24. The RPC
  classes need API 26 or core library desugaring in the app
  ([The RPC classes below API 26](#the-rpc-classes-below-api-26)).
- **ABIs:** `arm64-v8a`, `armeabi-v7a` and `x86_64`. There is no 32-bit `x86` library. The
  `armeabi-v7a` library is built with zephyr's own `-mfpu=vfpv4`, so it needs a CPU with VFPv4.
  Cortex-A8 and A9 cores lack it; every Android 7 device supports it.
- **16 KB pages (16 KiB):** the libraries are linked for 16 KB page alignment. Google Play requires
  it for apps that target Android 15 and later. Keep them uncompressed in the APK
  (`packaging { jniLibs.useLegacyPackaging = false }`, the default from minSdk 23). An app built
  against the AAR with AGP 9.5.0 passes `zipalign -c -P 16`, with the libraries stored.

**Size.** The libraries are stored uncompressed, so each ABI adds its library's full size to the
APK. From `2.3.0-sec.2-java.5` on, the natives carry no Trezor code and no protobuf, which makes
them 19 to 20 % smaller ([Build-layer patches](14-building-and-verifying.md#build-layer-patches)).
The libraries of a release build of the `2.3.0-sec.2-java.5` sources on 2026-11-19, with the
release's native libraries, against those of the `2.3.0-sec.2-java.4` AAR:

| ABI | Bytes | MB | MiB | Compressed inside the AAR | `java.4`: bytes | `java.4`: compressed |
|---|---|---|---|---|---|---|
| `arm64-v8a` | 16,041,640 | 16.04 | 15.30 | 6,014,906 bytes | 20,141,000 | 7,484,104 bytes |
| `armeabi-v7a` | 14,559,540 | 14.56 | 13.89 | 5,951,529 bytes | 18,019,980 | 7,165,617 bytes |
| `x86_64` | 19,013,608 | 19.01 | 18.13 | 7,097,656 bytes | 23,897,880 | 8,590,609 bytes |

So each ABI adds about 16 MB for `arm64-v8a`, 15 MB for `armeabi-v7a` and 19 MB for `x86_64`.
The size of a finished APK or of a Play delivery was not measured.

An app built as an Android App Bundle reaches each device from Google Play with the library of that
device's ABI only. The Android documentation, "About Android App Bundles", describes it. An APK you distribute
yourself carries every ABI unless you filter them; `x86_64` is mostly needed for the emulator:

```kotlin
android {
    defaultConfig {
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }
}
```

The `x86_64` library of `2.3.0-sec.2-java.5` ran on the Android emulator on 2026-11-19 and
2026-11-20, on API 24, API 35 and API 36, with 4 KB and 16 KB pages. In a minified app it ran
eight scenarios of closing and process exit, two of them also in an app without R8. Under CheckJNI
a debug app ran an offline wallet, a sync, `ZephyrDaemonRpc` (which read a mainnet node on API 36
with 16 KB pages) and TLS with it. The ARM libraries have not run on hardware
([What was tested](13-known-limitations.md#what-was-tested)).

## The RPC classes below API 26

`ZephyrDaemonRpc` sends its requests through the Apache HTTP client, which needs `java.time`;
Android has it from API 26 (8.0). On API 24 and 25 every request throws a `ZephyrError` with the
`NoClassDefFoundError` in its causes:

```text
The RPC classes need java.time: Android 8.0 (API 26) or newer, or core library desugaring in the app (user guide, "The RPC classes below API 26")
```

The section it names is this one. Releases up to `2.3.0-sec.2-java.4` print "chapter 6" in its
place; they mean the same section.

An app with minSdk 24 or 25 that uses the RPC classes enables core library desugaring in the app
module:

```kotlin
android {
    compileOptions { isCoreLibraryDesugaringEnabled = true }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
}
```

(Groovy: `coreLibraryDesugaringEnabled = true` and
`coreLibraryDesugaring 'com.android.tools:desugar_jdk_libs:2.1.5'`.) With these two lines
`ZephyrDaemonRpc` ran on API 24, on the emulator with earlier releases. The native wallet,
`ZephyrWalletFull`, does not use the HTTP client and needs neither. [The RPC classes](11-the-rpc-classes.md) covers what they do.

## The network

**Permission.** The AAR declares no permission. An app that connects to a node declares
`<uses-permission android:name="android.permission.INTERNET" />` in its manifest.

**Clear text.** The native wallet and the RPC classes open their own sockets. They do not read the
app's Network Security Configuration. On the emulator with an earlier release, an app with targetSdk
36 on API 35 reached an `http://` node without any `usesCleartextTraffic` setting. Android refuses
clear text by default for apps that target API 28 and later (Android documentation, "Network
security configuration"). But the documentation of `android:usesCleartextTraffic` notes that the
socket API does not honour that flag.

So the configuration neither blocks nor protects these connections.
`cleartextTrafficPermitted="false"` does not stop an `http://` node. The configuration's trust
anchors and pins do not apply to an `https://` node either. The native wallet's root certificates
are set with `ZephyrTls` ([TLS to a node](03-connecting-to-a-node.md#tls-to-a-node)).

## Storage

Android apps usually keep their data in the app's private storage or in their own database. Both
work:

- **Files:** give `createWallet` a path inside `context.getFilesDir()`. The wallet writes
  `<name>.keys` there when it is created, and `<name>` (the cache) on `save()`.
- **Bytes:** create the wallet without a path, take `wallet.getData()` (keys and cache) and store
  the two arrays yourself; open later with `ZephyrWalletFull.openWalletData(...)`. This suits an
  encrypted database ([Wallet bytes](02-wallets.md#wallet-bytes)).

## Backup

**What Auto Backup does.** Android's Auto Backup includes an app's files by default for apps that
target API 23 or newer. The files directory and databases go to the user's Google Drive, at most
25 MB per app. A device-to-device transfer copies them too (Android documentation, "Back up user
data with Auto Backup").

**What ends up in it.** Wallet files in `getFilesDir()`, or the bytes of `getData()` in a database,
are in the backup unless the app excludes them.

**What protects it.** Both the keys and the cache are encrypted with keys derived from the wallet
password, so a backup is as strong as that password. With an empty password anyone who has the
backup can open the wallet.

**How to keep wallet data out.** Whether wallet data belongs in a backup is the app's choice:

- `android:allowBackup="false"` in the manifest turns the cloud backup off. For an app that targets
  Android 12 or newer, on devices of some manufacturers, it does not stop a device-to-device
  transfer;
- `android:dataExtractionRules` (Android 12 and newer, for apps that target it) includes or
  excludes single files and directories, for the cloud backup and the device-to-device transfer
  separately;
- `android:fullBackupContent` (Android 11 and lower) includes or excludes them with one set of
  rules; a rule can require a device-to-device transfer or client-side encryption;
- files in `context.getNoBackupFilesDir()` are never backed up.

## Lifecycle and threads

A wallet is expensive to open and must be closed exactly once. It belongs to something that
outlives an Activity: a ViewModel, a bound service, or an application-wide holder. Close it (after
storing it) when that owner ends.

**Callbacks arrive on a thread you did not choose** ([Callbacks run on threads inside the core's calls](04-threads-and-listeners.md#callbacks-run-on-threads-inside-the-cores-calls)).
Post to the main thread before you touch views, and catch every exception inside the callback
([An exception thrown in a callback](04-threads-and-listeners.md#an-exception-thrown-in-a-callback)):

```java
Handler main = new Handler(Looper.getMainLooper());
wallet.addListener(new ZephyrWalletListener() {
  @Override
  public void onBalancesChanged(BigInteger balance, BigInteger unlocked, String assetType) {
    main.post(() -> viewModel.setBalance(assetType, balance, unlocked));
  }
});
```

This block uses Android classes, so it is not compiled with the tests.

While the app is in the background, Android may stop it at any time. Store the wallet when the app
goes to the background (`onStop`), and stop background syncing (`stopSyncing()`) if you do not
need it there. A save is not exclusive with background syncing, and `close(true)` is
([When to save](02-wallets.md#when-to-save)).

`close()` waits for the calls in flight and for the core's sync thread, without a time limit
([Closing a wallet that syncs](04-threads-and-listeners.md#closing-a-wallet-that-syncs)). Call it
on a worker thread, never on the main thread. In releases up to `2.3.0-sec.2-java.4`, a `close()`
while a sync ran could crash the process; let the sync end first there. The observations of that
section were made on Linux, apart from the waits it gives for the Android emulator. The closing
tests also ran on the Android emulator on 2026-11-19 and 2026-11-20
([What was tested](13-known-limitations.md#what-was-tested)).

## Logs

The core has no Android log output: nothing it logs appears in logcat (seen on the emulator with
earlier releases, API 24 and API 35). `ZephyrUtils.configureNativeLogging(path, false)` with a path
in `context.getFilesDir()` would send it to a file; on Android this has not been tried
([The native log](09-errors.md#the-native-log)).

zephyr-java's own messages (`ZephyrUtils.setLogLevel(...)`) are printed with `System.out`, and
Android shows `System.out` and `System.err` in logcat under those tags. Creating the first
`ZephyrRpcConnection` (an RPC class, or a wallet given a server URI) prints three SLF4J lines to
`System.err`, seen in logcat on the emulator with an earlier release. Among them are "Failed to
load class org.slf4j.impl.StaticLoggerBinder" and "Defaulting to no-operation (NOP) logger
implementation". The Apache HTTP client finds no logging
backend and logs nothing. The lines are harmless.

## A first test on a device

Call `GuideFirstProgram.smokeTest()` ([A first program](01-getting-started.md#a-first-program)) off
the main thread and log the result:

```java
new Thread(() -> Log.i("zephyr", "smoke test: " + GuideFirstProgram.smokeTest())).start();
```

A mainnet primary address (it starts with `ZEPHYR`) in logcat means that the device loaded the library of
its ABI and the core created a wallet.

## The shipped R8 rules

The AAR's `proguard.txt` is applied to every app that uses it. It keeps:

- every class with `native` methods and those methods' names, because the native library binds
  its exports by class and method name;
- `ZephyrWalletFull$WalletJniListener` with all members, because the native side looks it up by
  name and calls its five callbacks by name and signature;
- the two private fields `jniWalletHandle` and `jniListenerHandle` of `ZephyrWalletFull`, under
  these names, because the native side reads and writes them by name (from `2.3.0-sec.2-java.5`);
- the model packages `zephyr.wallet.model`, `zephyr.daemon.model` and `zephyr.common`, because
  Jackson maps the native JSON onto them by reflection, and the native side throws
  `zephyr.common.ZephyrError` by name;
- annotations and generic signatures, which Jackson reads at run time;
- from `2.3.0-sec.2-java.5` on, the library's inline subclasses of Jackson's `TypeReference` with
  their generic signatures. R8's full mode keeps a signature only on a class that a rule keeps.
  Without them the first `createWallet()` threw "TypeReference constructed without actual type
  information";
- from `2.3.0-sec.2-java.5` on, the private nested classes of `ZephyrWalletFull` into which
  Jackson maps accounts, subaddresses, the address book, key images, transaction sets and blocks.
  Without them `getAccounts` and `getTxs` failed with "Error deserializing json to class";
- from `2.3.0-sec.2-java.5` on, the request and response interceptors of the HTTP client, which
  R8 may rename but not merge. The client keeps one interceptor per class. R8 had merged three of
  them into one, and `ZephyrDaemonRpc` then failed with "Length required" on API 35;
- from `2.3.0-sec.2-java.5` on, the name of `ZephyrDaemonRpc`, which names its `java.util.logging`
  logger.

From `2.3.0-sec.2-java.5` on it also carries three `-dontwarn` rules: `org.brotli.dec.**`,
`org.ietf.jgss.**` and `org.slf4j.impl.StaticLoggerBinder`. The Apache HTTP client and its logging
interface refer to these classes, and neither the AAR nor Android provides them. Without the rules
an app built with R8 minification stopped with "Missing class" errors, with `2.3.0-sec.2-java.4`
as well.

The three run-time failures above were observed on the Android emulator on 2026-11-19 with AGP 9.5
in R8's full mode, each before the rule that fixed it. With the rules of the release a minified
app ran there. With an AAR from before the last change to the JNI bridge, on API 24 and API 35,
it created a wallet offline and read its accounts, subaddresses, address book, outputs and balance.
It synced against a node with a listener and read the node with `ZephyrDaemonRpc` (on API 24 it
got the documented `java.time` error). With an AAR from a release build of these sources, with
the release's native libraries and classes, it passed two closing scenarios in 200 runs on five
images, on 2026-11-19 and 2026-11-20. On 2026-11-20 it also ran five scenarios of closing and
process exit on four images, as expected. On the same day an AAR of a later release build,
which differs from that one only by a comment in its consumer rules, ran on all five images. It
passed the app's own checks and eight scenarios of closing and process exit
([What was tested](13-known-limitations.md#what-was-tested)).

The AAR's Android namespace is `dev.lateo.zephyrjava`. It names only the package of the AAR's
manifest; the Java packages are `zephyr.*`.

Your own listener classes need no rule: the native side calls the library's listener, which calls
yours through ordinary Java.
