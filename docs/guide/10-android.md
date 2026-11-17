# 10. Android

The AAR is the same library as the JAR, packaged for Android: the Java classes, the native library
for three ABIs and the R8 rules an app needs. This chapter covers what is different on Android:
the dependency, the devices it runs on, the RPC classes below API 26, the network, storage and
backup, lifecycle and threads, logs, a first test, and TLS to a node.

## The dependency

The repository goes where the project declares its repositories, which in a project created by
Android Studio is `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://loki-notfound.github.io/myzephyr-java/maven") }
    }
}
```

The dependency and the packaging line go into the app module's `build.gradle.kts`:

```kotlin
android {
    packaging { resources.excludes += "/META-INF/DEPENDENCIES" }
}

dependencies {
    implementation("io.github.loki-notfound:zephyr-java-android:2.3.0-sec.2-java.4")
}
```

Android Studio's project template also sets `repositoriesMode` to `FAIL_ON_PROJECT_REPOS` in that
block. A `repositories { }` block in the module's build file then stops the build with "Build was
configured to prefer settings repositories over project repositories but repository '…' was added
by build file '…'", so put the repository line in the settings file.

The same in the Groovy DSL (`settings.gradle` and `app/build.gradle`):

```groovy
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = 'https://loki-notfound.github.io/myzephyr-java/maven' }
    }
}
```

```groovy
android {
    packaging { resources.excludes += '/META-INF/DEPENDENCIES' }
}

dependencies {
    implementation 'io.github.loki-notfound:zephyr-java-android:2.3.0-sec.2-java.4'
}
```

With a version catalog, `gradle/libs.versions.toml` gets the entry and the module uses
`implementation(libs.zephyr.java.android)` (Groovy: `implementation libs.zephyr.java.android`):

```toml
[versions]
zephyrJava = "2.3.0-sec.2-java.4"

[libraries]
zephyr-java-android = { module = "io.github.loki-notfound:zephyr-java-android", version.ref = "zephyrJava" }
```

The AAR is published as `zephyr-java-android`
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
25 lack). Gradle does not report a conflict when your app or another library asks for Jackson
2.14 or newer: it resolves the highest version for the whole app, zephyr-java included, and the
build succeeds. On API 26 and newer that is fine (zephyr-java was tested on Jackson 2.21.7 on the
JVM, the coexistence test); on API 24 and 25 the app then fails at run time. Check what the app
really gets:

```sh
./gradlew :app:dependencies --configuration releaseRuntimeClasspath | grep jackson
```

A line such as `jackson-databind:2.13.5 -> 2.21.7` means that another dependency lifted Jackson.
Then minSdk 24 and that dependency do not go together: raise minSdk to 26, or use a version of the
other library that stays on Jackson 2.13. On Android zephyr-java has run with 2.13.5 only.

## Devices

- **minSdk 24** (Android 7.0). The native wallet, `ZephyrWalletFull`, runs from API 24; the RPC
  classes need API 26 or core library desugaring in the app ([below](#the-rpc-classes-below-api-26)).
- **ABIs:** `arm64-v8a`, `armeabi-v7a` and `x86_64`. There is no 32-bit `x86` library. The
  `armeabi-v7a` library is built with zephyr's own `-mfpu=vfpv4`, so it needs a CPU with VFPv4
  (Cortex-A8 and A9 cores lack it); every Android 7 device supports it.
- **16 KB pages:** the libraries are linked for 16 KB page alignment, which Google Play requires for
  apps that target Android 15 and later. Keep them uncompressed in the APK
  (`packaging { jniLibs.useLegacyPackaging = false }`, the default from minSdk 23); an app built
  against the AAR with AGP 9.5.0 passes `zipalign -c -P 16`, with the libraries stored.
- **Size:** the libraries are stored uncompressed, so each ABI adds its library's full size to the
  APK: about 20 MB for `arm64-v8a`, 18 MB for `armeabi-v7a` and 24 MB for `x86_64`.

Built as an Android App Bundle, the app reaches each device from Google Play with the library of
that device's ABI only (Android documentation, "About Android App Bundles"). An APK you distribute
yourself carries every ABI unless you filter them; `x86_64` is mostly needed for the emulator:

```kotlin
android {
    defaultConfig {
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }
}
```

## The RPC classes below API 26

`ZephyrDaemonRpc` and `ZephyrWalletRpc` send their requests through the Apache HTTP client, which
needs `java.time`; Android has it from API 26 (8.0). On API 24 and 25 every request throws a
`ZephyrError` "The RPC classes need java.time: Android 8.0 (API 26) or newer, or core library
desugaring in the app (user guide, chapter 6)", with the `NoClassDefFoundError` in its causes.

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
`ZephyrDaemonRpc` runs on API 24. The native wallet, `ZephyrWalletFull`, does not use the HTTP
client and needs neither.

## The network

**Permission.** The AAR declares no permission. An app that connects to a node declares
`<uses-permission android:name="android.permission.INTERNET" />` in its manifest.

**Clear text.** The native wallet and the RPC classes open their own sockets and do not consult
Android's network security configuration. An `http://` node worked on API 35 from an app with
targetSdk 36 and no `usesCleartextTraffic` setting, although Android's default for apps that target
API 28 and later is to refuse clear text (Android documentation, "Network security
configuration"; the documentation of `android:usesCleartextTraffic` notes that the socket API does
not honour that flag). So the configuration neither blocks nor protects these connections:
`cleartextTrafficPermitted="false"` does not stop an `http://` node, and the configuration's trust
anchors and pins do not apply to an `https://` node: the native wallet's root certificates are set
with `ZephyrTls` ([TLS to a node](03-connecting-to-a-node.md#tls-to-a-node)).

## Storage

Android apps usually keep their data in the app's private storage or in their own database. Both
work:

- **Files:** give `createWallet` a path inside `context.getFilesDir()`. The wallet writes
  `<name>.keys` and `<name>` there on `save()`.
- **Bytes:** create the wallet without a path, take `wallet.getData()` (keys and cache) and store
  the two arrays yourself; open later with `ZephyrWalletFull.openWalletData(...)`. This suits an
  encrypted database.

## Backup

Android's Auto Backup includes an app's files by default for apps that target API 23 or newer: the
files directory and databases go to the user's Google Drive (at most 25 MB per app), and a
device-to-device transfer copies them too (Android documentation, "Back up user data with Auto
Backup"). Wallet files in `getFilesDir()`, or the bytes of `getData()` in a database,
are therefore in the backup unless the app excludes them.

Both the keys and the cache are encrypted with keys derived from the wallet password, so a backup
is as strong as that password; with an empty password anyone who has the backup can open the
wallet. Whether wallet data belongs in a backup is the app's choice:

- `android:allowBackup="false"` in the manifest turns the cloud backup off; for an app that targets
  Android 12 or newer it does not stop a device-to-device transfer;
- `android:dataExtractionRules` (Android 12 and newer) and `android:fullBackupContent` (Android 11
  and lower) include or exclude single files and directories, for the cloud backup and the
  device-to-device transfer separately;
- files in `context.getNoBackupFilesDir()` are never backed up.

## Lifecycle and threads

A wallet is expensive to open and must be closed exactly once, so it belongs to something that
outlives an Activity: a ViewModel, a bound service, or an application-wide holder. Close it (after
storing it) when that owner ends.

**Callbacks arrive on a native thread** ([Callbacks run on threads you did not create](04-threads-and-listeners.md#callbacks-run-on-threads-you-did-not-create)). Post to
the main thread before you touch views:

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

## Logs

The core has no Android log output: nothing it logs appears in logcat (seen on the emulator, API 24
and API 35).

zephyr-java's own messages (`ZephyrUtils.setLogLevel(...)`) are printed with `System.out`, and
Android shows `System.out` and `System.err` in logcat under those tags. When the first
`ZephyrRpcConnection` is created (an RPC class, or a wallet given a server URI), `System.err` shows
three SLF4J lines ("Failed to load class org.slf4j.impl.StaticLoggerBinder",
"Defaulting to no-operation (NOP) logger implementation"): the Apache HTTP client finds no logging
backend and logs nothing. They are harmless.

## A first test on a device

Call it off the main thread and log the result:

```java
new Thread(() -> Log.i("zephyr", "smoke test: " + GuideFirstProgram.smokeTest())).start();
```

A mainnet address (it starts with `ZEPHYR`) in logcat means that the device loaded the library of
its ABI and the core created a wallet.

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
