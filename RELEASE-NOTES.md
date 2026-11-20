# Release notes

One section per release, newest first. Tags are `v<zephyr version>-sec.<N>-java.<M>`; the Maven
version is the tag without the `v`. Changes to the Java API are listed under "Breaking changes".
A release's date is the date of its signed tag and of its GitHub release. Chapter numbers of the
user guide in a release's section refer to the guide of that release.

**Not affiliated with ZephyrProtocol.** An independent project. Report Zephyr vulnerabilities to the
ZephyrProtocol team, not here.

## v2.3.0-sec.2-java.5

The fifth release on the same core as `v2.3.0-sec.2-java.1`: the closing and listener defects of
`java.4` are fixed, every wallet call throws `ZephyrError`, the library's own logging keeps
secrets out, and the artifacts move to Maven Central under `dev.lateo`. The wallet RPC client is
removed, and the natives carry no Trezor code. **Breaking changes** below.

### Built from

The same zephyr-backports release set of 2026-11-07 as the previous releases, unchanged:

| | Tag | Commit | Tag subject |
|---|---|---|---|
| zephyr | `v2.3.0-sec.2` | `8c6a120256b4bd887d6e7167d929d76e3507e0e5` | zephyr v2.3.0 + 30 security backports from Monero |
| zephyr-cpp | `v2.3.0-sec.1` | `3a2ee602ee28e6b7941b617ad4706349ad992641` | zephyr-cpp master (4a99272f6d7a171cf6625016939094637459975a) + 4 security backports from monero-cpp |

The root certificates are those of 2026-11-05, as in `java.4`; curl had published no newer file
on 2026-11-19. New build setting: zephyr is configured with `USE_DEVICE_TREZOR=OFF`, and depends
leaves out protobuf (below, Changed).

### Breaking changes

- **Coordinates and repository.** The groupId is `dev.lateo` (was `io.github.loki-notfound`); the
  artifactIds `zephyr-java` and `zephyr-java-android` and the Java packages are unchanged. The
  artifacts are on Maven Central, so a build needs no repository line. The Maven repository on
  the project's GitHub Pages is taken down with this release, older versions included. A build
  that still resolves `java.4` or an older release from it stops resolving, whether it upgrades
  or not. Either move to `java.5` from Maven Central, or take the older release's JAR or AAR from
  its GitHub release, check it against that release's signed `SHA256SUMS` and use it as a file
  dependency. Add its three runtime dependencies then: `com.fasterxml.jackson.core:jackson-core`
  and `jackson-databind` 2.13.5, and `org.apache.httpcomponents.client5:httpclient5` 5.2.1. The
  site moves to `https://java-docs.lateo.dev`.
- **The wallet RPC client is removed**, with the jeromq dependency that only its ZMQ listener
  used (`org.zeromq:jeromq:0.5.2`, optional, and its `eu.neilalexander:jnacl:1.0.0`; the AAR's
  compile-only jeromq). Removed public types: `zephyr.wallet.ZephyrWalletRpc` (its 5 constructors
  and 170 public methods), `ZephyrWalletRpc.IncomingTransferComparator`,
  `ZephyrWalletRpc.OutputComparator` and `zephyr.common.SslOptions` (its constructor, 5 getters
  and 5 setters). Beyond the `ZephyrWallet` interface, `ZephyrWalletRpc` had `getProcess()`,
  `stopProcess()`, `stopProcess(boolean)`, `getRpcConnection()`, `openWallet(String, String)`,
  `openWallet(ZephyrWalletConfig)`, `createWallet(ZephyrWalletConfig)`,
  `getMnemonicLanguages()`, `stop()`, `setDaemonConnection(ZephyrRpcConnection, Boolean,
  SslOptions)`, `getAccounts(boolean, String, boolean)`, `getAccount(int, boolean, boolean)` and
  `getSubaddresses(int, List, boolean)`. No public member of a kept class was removed.
  `ZephyrDaemonRpc`, `ZephyrRpcConnection` and `ZephyrRpcError` stay.
- **Exception types.** Every public method of `ZephyrWalletFull` and `ZephyrUtils` throws
  `ZephyrError`, with the message it had. That includes the core's failures. Several calls let
  them through as `java.lang.Exception` or `IOException` (a wrong password among them), and some
  native calls did not catch them at all (below, Fixed). It also includes the argument checks that
  threw `AssertionError`, the unknown hashes of `getTransfers` and `getOutputs` and the "not
  implemented" calls, which threw `RuntimeException`, and the word count of `validateMnemonic`,
  which threw `java.lang.Error`. A `null` argument threw `NullPointerException`. Now it gives a
  `ZephyrError` with the runtime's text, or "Must provide ..." in `getTxs(String...)`,
  `relayTx(ZephyrTxWallet)`, `relayTxs(List)` and `importMultisigHex(String...)`.
  `toAtomicUnits(double)` throws `ZephyrError` "Amount is not a finite number: " for NaN and the
  infinities. `OutOfMemoryError` stays as it was, and a native library that cannot be loaded gives
  the Java runtime's own errors (guide, "What is not a ZephyrError").
- **Calls refused inside a listener callback.** They throw `ZephyrError` "Cannot call <call> from
  inside a listener callback; call it after the callback has returned": every `sync`, `scanTxs`,
  `rescanBlockchain`, `startSyncing`, `rescanSpent`, `makeMultisig`, `exchangeMultisigKeys`;
  every call that relays (`createTx`, `createTxs`, `sweepUnlocked`, `sweepOutput` and `sweepDust`
  with relay, `relayTx`, `relayTxs`, `submitTxs`, `submitMultisigTxHex`); `importKeyImages`,
  `importMultisigHex`, `importOutputs`; and the calls that persist the wallet, `save`, `moveTo`,
  `changePassword` and `getData`. A callback runs in the middle of a refresh: a sync from it
  deadlocked, and a save from an output callback would write a transaction half processed.
  `close()` from a callback, or from inside one of the wallet's own calls, throws "Cannot close
  the wallet from inside one of its own calls or listener callbacks".
- **Calls cut short by `close()` throw.** A `sync()`, `rescanBlockchain()`, `scanTxs()` or
  `importMultisigHex()` that `close()` stops throws `ZephyrError` "Wallet is closed" instead of
  returning a result that looks complete.
- **`close()` waits.** It returns only when no other thread is inside a call of the wallet and the
  core's background sync thread has ended, without a time limit. Normally that takes the current
  batch of blocks. A node request under way can take up to the core's timeout of 3 min 30 s. A
  blocking listener callback, or the rare cases in the guide's "Closing a wallet that syncs", can
  take longer. An interrupt does not end the wait.
- **Callbacks of two threads can overlap.** The bridge no longer serialises callbacks: background
  syncing and a relaying call on another thread can deliver at the same time. Listeners must be
  thread-safe.
- **Messages name the guide's sections** instead of "chapter 6": the TLS hint of "Wallet is not
  connected to daemon" for an `https://` node names "TLS to a node", and the `java.time` message
  of the RPC classes names "The RPC classes below API 26".

### Security

- `ZephyrWalletFull` at `FINE` no longer logs the transaction config of `createTxs` or the first
  5,000 characters of the transactions of `createTxs` and `sweepUnlocked`, which held each
  transaction's secret key. It logs the number of transactions and their hashes.
- `ZephyrRpcConnection` from `setLogLevel(2)`, its responses at level 3 and its debug stack traces
  print the values of secret names as `<redacted>`. One list of 16 names applies alike to requests,
  responses and the parameters of `ZephyrRpcError`, at any depth and without regard to case. The
  names are `password`, `old_password`, `new_password`, `seed`, `seed_offset`, `spendkey`,
  `viewkey`, `tx_key`, `key`, `tx_key_list`, `tx_metadata`, `tx_metadata_list`, `signed_txset`,
  `multisig_txset`, `multisig_info` and `info`. The user name and password written into a URI are
  redacted in what it prints, and in its `toString()`.
- `ZephyrRpcError.toString()` redacts the same names in the request parameters, which `java.4`
  printed in full, passwords included. `getRpcParams()` still holds them.

### Fixed

- **`close()` during a `sync()` on another thread crashed the process** in `java.4`, with a
  segmentation fault, in 4 of 4 runs with the released JAR (2026-11-17). `close()` now waits for
  the calls in flight, every native call of the wallet runs inside a lock that `close()` waits
  for, and the sync ends with "Wallet is closed".
- **`close()` while background syncing scanned left a native thread attached**, and the JVM never
  exited (3 of 3 runs with the released JAR, 2026-11-17), also with `stopSyncing()` right before
  (2 of 2). The callbacks delivered meanwhile failed with "Wallet is closed" on stderr, hundreds of
  times. Now `close()` stops the core's sync thread, again until it has ended, and joins it before
  the wallet is freed. Callbacks return at once once the wallet is closed.
- **An exception from a listener during background syncing left a native thread attached**, and
  the JVM never exited (3 of 3 runs with the released JAR, 2026-11-17). Now every listener runs in
  its own protection: its `Throwable` is logged at `WARNING` without the callback's data, and the
  other listeners are still called. The bridge never leaves a callback thread attached: a native
  thread of the core is attached once, as a daemon thread, on its first callback, and detached
  when it ends, before `close()` returns (below, Changed).
- **Adding and removing listeners while background syncing ran could crash the process.** The
  change raced the core's sync thread in the core's unsynchronised listener set. A test that did
  so in a loop crashed a development build with `java.4`'s pattern in 8 of 8 runs (2026-11-18);
  `java.4` itself was not run. Now the native listener is added once, on the wallet's first
  listener, and later changes only switch its delivery.
- **`close(true)` saved while the core's sync thread could still be refreshing** the wallet. That
  was observed on 2026-11-18 in 3 of 3 runs, with a development build whose save order at close was
  `java.4`'s. It also did not catch a failing save in native code (read in the code). Now it saves
  after that thread has ended. When the save fails the wallet is closed and freed all the same, and
  `close(true)` throws "Wallet was closed, but saving it failed: " with the core's reason.
- **`close()` from a callback of background syncing aborted the process** ("trying joining
  itself"), observed on 2026-11-18 with a development build whose closing code was `java.4`'s. Now
  it is refused.
- **Calls after `close()`**: `getReserveProofWallet` on a closed wallet crashed the process
  (observed on 2026-11-18 with the same build), and `changePassword` had no check (it failed with
  an unrelated error). Now every call of a closed wallet throws "Wallet is closed".
- **The wrong amounts of `toAtomicUnits(double)`.** Values that `Double.toString` prints with 19
  or more decimals, between 0.001 and 0.01, came out about 8 % too high (12 of 2,000,000 random
  values). Values printed in scientific notation, below 0.001 and from 10^7, threw
  `NumberFormatException`. Now every finite value converts, and decimals beyond the twelfth are
  dropped as before.
- **A failure of the core in some native calls ended the process.** 21 native functions did not
  catch the core's exceptions, among them the account and subaddress calls, `getAddress`, the key
  getters and five of the seven native functions of `ZephyrUtils`. Several more parsed their input
  before their `try` block. Observed on 2026-11-19 with a development build whose bridge was
  `java.4`'s in those places: each of these ended the process with `SIGABRT`. They were
  `createSubaddress` with an account index out of range, `createTx`, `sweepUnlocked`,
  `sweepOutput` and `getPaymentUri` with an amount or fee of 2^64 or more, and
  `ZephyrUtils.binaryBlocksToMap` with a block that does not parse. `getReserveProofAccount` with
  an amount of 2^64 or more ended the process the same way, in the bridge's own conversion of the
  amount. Now every native call turns these failures into a `ZephyrError` with the failure's
  message.
- **`ZephyrUtils.mapToBinary` crashed the process for a result of more than about 1 MB**: the
  bridge copied the bytes through an array on the native stack (observed on 2026-11-19 with the
  same build). Now it copies them directly.
- **A `null` attribute key or value aborted the process** in `getAttribute` and `setAttribute`
  (the bridge built a string from a null pointer). Now it is refused with "Must provide attribute
  key" or "Must provide attribute value".
- **A minified app could not be built against the AAR, nor use it.** R8 stopped on classes that
  the Apache HTTP client and its logging interface refer to (`org.brotli.dec.BrotliInputStream`,
  `org.ietf.jgss.*`, `org.slf4j.impl.StaticLoggerBinder`) unless the app added its own rules;
  `java.4` failed the same way (2026-11-19, AGP 9.5, R8 full mode). Built with those rules, a
  minified app failed at run time in three ways, observed on the Android emulator on 2026-11-19.
  Its first `createWallet()` threw "Internal error: TypeReference constructed without actual type
  information": R8's full mode had dropped the generic signatures that Jackson reads. With those
  kept, `getAccounts` and `getTxs` failed with "Error deserializing json to class", since R8 had
  renamed or removed the library's private JSON classes. With those kept as well,
  `ZephyrDaemonRpc` failed on API 35 with "Length required". R8 had merged three request
  interceptors of the HTTP client into one class, the client keeps one interceptor per class, and
  it then refused to send a request body without its length. The AAR's consumer rules now cover
  all of this (below, Changed), and a minified app ran on the emulator (below, Tested).
- A wallet whose node connection failed right after it was opened or created was never freed;
  it is now closed before the exception is thrown.
- A listener given to `sync(listener)` that removed itself made the sync throw "Listener is not
  registered with wallet" at its end; it no longer does.

### Changed

- **The natives carry no Trezor code and no protobuf.** zephyr is configured with
  `USE_DEVICE_TREZOR=OFF` (its `device_trezor` library becomes the stub with an empty
  registration), depends builds no protobuf, and the JNI library no longer links protobuf or
  libusb directly. On Linux hidapi with libusb and eudev stays, for zephyr's Ledger code. Exports
  and the needed libraries are unchanged. The natives are 17 to 20 % smaller. In a release build
  of the final code on 2026-11-19: `linux-x86_64` 19,076,344 bytes (`java.4`: 23,206,736),
  `linux-arm64` 16,702,608 (20,053,160), `arm64-v8a` 16,041,640 (20,141,000), `armeabi-v7a`
  14,559,540 (18,019,980) and `x86_64` 19,013,608 (23,897,880).
- The consumer R8 rules keep the two native handle fields of `ZephyrWalletFull` under their names,
  which the bridge reads by name and Java no longer reads. They keep the library's `TypeReference`
  subclasses with their generic signatures, and the private JSON classes of `ZephyrWalletFull`,
  which Jackson reads by reflection. They keep the HTTP client's request and response
  interceptors from being merged into one class, and the name of `ZephyrDaemonRpc`, which names
  its logger. And they carry the three `-dontwarn` lines above.
- The AAR's Android namespace is `dev.lateo.zephyrjava` (was `io.github.lokinotfound.zephyrjava`);
  it names only the package of the AAR's manifest.
- Both POMs carry what Maven Central requires (name, description with the not-affiliated
  sentence, url, licences, developer, scm). They name the MIT licence of the library and the
  Mozilla Public License 2.0 of the shipped root certificates (`cacert.pem`). The JAR's POM no
  longer names the Apache License 2.0, as `java.4`'s did. The OSSRH distribution settings and the
  old `release` profile are gone.
- The release build writes the Maven Central bundle (`dist/<version>/central/`), which
  `bin/sign_central_bundle.sh` signs. Every file Central serves is a release file under the signed
  `SHA256SUMS`, except the AAR's Javadoc jar, a copy of the JAR's under the AAR's name, which
  `manifest.json` lists. The JAR's POM and the AAR's Gradle module metadata are new release files.
- The native listener is added to the core once, on the wallet's first listener, and stays until
  `close()`. A listener given to `sync(listener)` counts as added. A wallet that never gets a
  listener costs what it cost in `java.4`. A wallet whose listener was added and removed pays the
  core's per-block listener work, about 11 % per scan in a measurement of 2026-11-18. Add the first
  listener before `startSyncing()`.
- **The JNI bridge attaches each native thread of the core to the JVM once**, as a daemon thread,
  on its first callback, and detaches it when the thread ends. Before, as in `java.4`, it attached
  the thread for each callback and detached it afterwards. In a scan of about 18,000 callbacks on
  Linux on 2026-11-19, a thread was attached 18,093 times before the change and once after it.
  On one Android emulator image, apps crashed during background syncing before the change, in 5
  of 80 runs, and in none of 80 after it (below, Tested).
- The JNI bridge throws `ZephyrError` for every core exception, in every native call, and
  `OutOfMemoryError` for a failed allocation, also while it copies a call's arguments. It deletes
  the local references of each callback and checks each class lookup in `JNI_OnLoad`.
- The Javadoc of the wallet model's classes is complete; a missing comment in the classes kept
  complete fails the Javadoc step. Four model classes got an explicit documented constructor.

### Documentation

- The user guide describes `java.5`: threads, callbacks and closing (the calls refused inside a
  callback, what bounds `close()`, the rule to close every wallet before the process ends),
  exceptions and logging, amounts, the RPC classes without the wallet RPC client, the
  distribution, and the known limitations with a list of what `java.5` fixed. What remains for
  readers of `java.4` stays, marked as such.
- The README follows.

### Tested

On Linux `x86_64` (Debian 13, OpenJDK 21), from 2026-11-18 to 2026-11-19, with development builds
of these sources, against a mainnet node, read-only; nothing was sent or relayed:

- the offline tests, 72 on the final code. Among them are every public method of a closed
  wallet, the refusals inside a callback, a failed save at close and `close()` racing calls on
  another thread. Nine failing cases in eight native calls are covered too: failures of the core
  in six calls, the bridge's own conversion of an amount, and a large conversion. So are the
  amounts table and the logging with planted secrets;
- the closing tests in child processes (each fails on a crash, a hang or a thread left attached)
  on the final code: 45 passed, 20 opt-in runs skipped. Among them `close()` during a foreground
  `sync()` in 10 of 10 runs, right after `startSyncing()` and `stopSyncing()`, and listeners added
  and removed in a loop during background syncing. Against the node `close()` returned within
  199 ms, apart from the tests that held a refresh or a node request on purpose. Against a
  socket that never answered it waited 208 s, the core's timeout;
- the read-only integration tests and the TLS tests;
- all of these again under `-Xcheck:jni` on the final code, without a warning or fatal error from
  the JNI checks;
- on 2026-11-18, the offline and closing tests of the code as it stood then (65 and 35 tests,
  before the last closing fixes) also on the Linux `x86_64` library built in the release
  container;
- release builds in the containers on 2026-11-19 of two earlier states of these sources, three
  and two builds, each set with identical `SHA256SUMS` and identical Maven Central bundles. A
  Gradle JVM program resolved and ran the JAR from a local copy of the bundle.

On the Android emulator on 2026-11-19, the `x86_64` library, against the same node, read-only.
The images were API 24, API 35 with 16 KB pages (a Google APIs image, build `AE3A.240806.043`),
and API 35 and API 36 with 4 KB pages:

- the AAR of a release build of the final code, in a minified app (AGP 9.5, R8 full mode) and in
  the same app built without R8, neither debuggable, on all four images. Two closing scenarios
  ran 20 times per build and image, each in its own process: `close()` during busy background
  syncing, and a throwing listener during background syncing. All 320 runs passed without a
  crash, each with one callback thread of the core. These runs covered those two scenarios only;
- AARs of these sources before the last change to the JNI bridge, on API 24 and on the 16 KB
  image. In debug apps under ART's CheckJNI the closing tests ran as instrumented tests, 19 of 19
  per image. They cover `close()` during a foreground `sync()`, during busy background syncing and
  right after `stopSyncing()`, a throwing listener, the refusals in a callback and a process ended
  with a wallet syncing. The debug apps also ran an offline wallet, a sync and a reopen from
  bytes, `ZephyrDaemonRpc` and TLS to a node with a publicly signed certificate. The minified app,
  with the consumer rules of this release, read an offline wallet's accounts, subaddresses,
  address book, outputs and balance. It synced with a listener and read the node with
  `ZephyrDaemonRpc` on API 35. With rules from before the interceptor rule, it ran the same
  closing scenarios from its own code under CheckJNI, 19 of 19 on API 24.

With those earlier AARs, which attached the core's thread for each callback, apps that are not
debuggable crashed with `SIGSEGV` on the 16 KB image during background syncing. That happened in
2 of 19 runs of the closing scenarios, in 3 of 24 repeated runs, and in 5 of 80 runs of the same
matrix as above. On this image, processes without zephyr-java code crashed the same way, also at
boot before the app was installed. During the matrix, with either AAR, no process crashed on the
three 4 KB images. The runs under CheckJNI showed no JNI error in a process of zephyr-java. The
ARM libraries have not run on hardware.

### The dependency

Android: `dev.lateo:zephyr-java-android:2.3.0-sec.2-java.5`; the JVM:
`dev.lateo:zephyr-java:2.3.0-sec.2-java.5`, both from Maven Central. The `packaging` line and the
dependencies are those of the earlier releases; the guide's "Android" says where they go.

### Verification

```sh
gpg --import KEYS.asc
git verify-tag v2.3.0-sec.2-java.5
gpg --verify SHA256SUMS.asc && sha256sum -c SHA256SUMS
```

## v2.3.0-sec.2-java.4

The fourth release on the same core as `v2.3.0-sec.2-java.1`: `https://` nodes work on Android,
with root certificates shipped in the library, and a daemon URI without a port works. No breaking
change.

### Built from

The same zephyr-backports release set of 2026-11-07 as the previous releases, unchanged:

| | Tag | Commit | Tag subject |
|---|---|---|---|
| zephyr | `v2.3.0-sec.2` | `8c6a120256b4bd887d6e7167d929d76e3507e0e5` | zephyr v2.3.0 + 30 security backports from Monero |
| zephyr-cpp | `v2.3.0-sec.1` | `3a2ee602ee28e6b7941b617ad4706349ad992641` | zephyr-cpp master (4a99272f6d7a171cf6625016939094637459975a) + 4 security backports from monero-cpp |

New build inputs: the root certificates of 2026-11-05 (Mozilla's list as curl publishes it,
`https://curl.se/ca/cacert-2026-11-05.pem`, 118 certificates, sha256
`427c064216761a1e72a8b30f0d954d08cb6e4435a82091fb865afd20c8497285`), and the build-layer patch
`0006-openssl-android-environment` (guide chapter 9).

### Fixed

- `https://` nodes on Android. On the Android images this was measured on (the emulator, API 24
  and API 35) the native wallet could not verify any `https://` node: OpenSSL ignored
  `SSL_CERT_FILE` and `SSL_CERT_DIR` in an app process, because it asked `getauxval(AT_SECURE)`,
  which was set in the app process on both. The build now patches OpenSSL to compare the user and
  group ids, as on every platform without `getauxval`.
- **Correction:** in `v2.3.0-sec.2-java.1` to `v2.3.0-sec.2-java.3` the guide, the README and
  these notes told Android apps to set `SSL_CERT_FILE` or `SSL_CERT_DIR`. On the emulator images
  named above that had no effect, so an `https://` node could not be verified there; other
  devices were not measured. A node that seemed to work with its scheme written `HTTPS://` was
  spoken in clear text (below). On the JVM the variables worked, and they still work when
  `ZephyrTls` is not used.
- A daemon URI without a port: the core connected to port 0 and blocked for minutes without an
  error. An `http` or `https` URI without a port now gets the scheme's default port (80, 443).
- `ZephyrUtils.getVersion()` returned "unknown" on Android: the AAR did not carry
  `zephyr-java.properties`. It does now.
- A daemon URI whose scheme was not written in lower case (`HTTPS://…`) was spoken in clear text
  by the core; the scheme now reaches the core in lower case.

### Added

- `zephyr.common.ZephyrTls`: the root certificates the native wallet trusts for `https://` nodes.
  By default the root certificates shipped in the library and nothing else, applied automatically
  on Android before the first `https://` connection of a wallet (unless the environment already
  names `SSL_CERT_FILE` or `SSL_CERT_DIR`); the system store, the user's certificates, the app's
  own certificates and one exact file are options. Nothing changes on the desktop JVM unless the
  application calls `ZephyrTls`.
- The shipped root certificates (`zephyr/common/cacert.pem`, Mozilla Public License 2.0), renewed
  with each release.
- `ZephyrTls.downloadCertificates`, opt-in: fetches the current root certificates from curl.se
  (or another `https` source, optionally checked by sha256) for apps that want them before the
  next release. The library never calls it.

### Changed

- `ZephyrWalletFull.getDaemonConnection()` reports the URI with the default port when it was set
  without one.
- When a call that needs the node fails with "Wallet is not connected to daemon" and the daemon
  URI is `https://`, the message adds ": the node is an https node; its certificate may not be
  trusted (see ZephyrTls and the user guide, chapter 6)". `isConnectedToDaemon()` is unchanged.
- The JNI bridge checks for a pending Java exception after the calls that convert strings, so
  `-Xcheck:jni` reports no "JNI call made without checking exceptions" warnings any more.

### Documentation

- Guide chapter 6 "TLS to a node" rewritten (`ZephyrTls`, the shipped certificates, a private CA,
  the download, what cannot be done, the port); chapters 2, 8 and 9 and the README follow.

### Tested

- On the JVM (Linux) against a mainnet node behind a publicly signed certificate: a wallet
  without any `ZephyrTls` call connects through the host's store; with the shipped certificates
  applied it connects and reads the daemon height; with an empty CA file it does not connect,
  and `sync()` throws the error with the TLS hint; a URI without a port connects on 443; the
  download returns 118 certificates, a wrong checksum leaves the target untouched. Unit and
  integration tests under `-Xcheck:jni`: no warning.
- On the Android emulator (`x86_64`), with this release's AAR in debug builds (CheckJNI on), on
  API 24 (with and without core library desugaring) and on API 35 with 16 KB pages: the checks of
  `java.3`, and `https://` to a mainnet node without any setup and with a URI without a port
  (connected, a restored wallet synced over TLS, reopened from its bytes) and, on API 24 without
  desugaring and on API 35, with the scheme written `HTTPS://` (connected, reported as
  `https://`); the system store alone (connected on API 35; on API 24 not, with the TLS hint);
  exactly one file (an empty file refused, ISRG Root X1 alone accepted); the download of the
  current root certificates. The ARM libraries have not yet run on hardware.

### The dependency

Android: `io.github.loki-notfound:zephyr-java-android:2.3.0-sec.2-java.4`; the JVM:
`io.github.loki-notfound:zephyr-java:2.3.0-sec.2-java.4`. The repository line, the `packaging`
line and the dependencies are those of the earlier releases; guide chapter 6 says where they go.

### Verification

```sh
gpg --import KEYS.asc
git verify-tag v2.3.0-sec.2-java.4
gpg --verify SHA256SUMS.asc && sha256sum -c SHA256SUMS
```

## v2.3.0-sec.2-java.3

The third release on the same core as `v2.3.0-sec.2-java.1`: a fix in the JNI bridge for
debuggable Android apps, a clear error for the RPC classes on Android 7, and the Android
documentation. No breaking change.

### Built from

The same zephyr-backports release set of 2026-11-07 as the previous releases, unchanged:

| | Tag | Commit | Tag subject |
|---|---|---|---|
| zephyr | `v2.3.0-sec.2` | `8c6a120256b4bd887d6e7167d929d76e3507e0e5` | zephyr v2.3.0 + 30 security backports from Monero |
| zephyr-cpp | `v2.3.0-sec.1` | `3a2ee602ee28e6b7941b617ad4706349ad992641` | zephyr-cpp master (4a99272f6d7a171cf6625016939094637459975a) + 4 security backports from monero-cpp |

### Fixed

- The JNI bridge no longer releases `null` strings. Debuggable Android apps, in which ART's
  CheckJNI is always on, aborted when a wallet was created from a mnemonic or keys, or opened,
  with a connection without credentials: the bridge released the connection's `null` user name
  and password. This affected `v2.3.0-sec.2-java.1` and `v2.3.0-sec.2-java.2`; release builds and
  the JVM (without `-Xcheck:jni`) were not affected. Every string release in the bridge now checks
  for `null` first, and the natives that read a path, a password or a byte array accept `null`
  like the others.
- Memory leaks in four calls of the native bridge, `ZephyrUtils.binaryToMap`,
  `ZephyrUtils.binaryBlocksToMap`, `ZephyrWalletFull.getSubaddresses` and
  `getAddressBookEntries`, which never released the array passed to them, and in `changePassword`,
  which never released the two password strings.

### Changed

- The RPC classes (`ZephyrDaemonRpc`, `ZephyrWalletRpc`) report a missing `java.time` on
  Android 7.x (API 24 and 25) with a `ZephyrError` that names the remedy, Android 8.0 or core
  library desugaring in the app, on every request, instead of a bare `NoClassDefFoundError`.

### Documentation

- Guide chapter 6 (Android) completed: where the repository line goes, with the Groovy and
  version catalog forms; the RPC classes below API 26 and the desugaring lines; how to check which
  Jackson the app gets; ABI sizes and App Bundles; the `INTERNET` permission and clear text; Auto
  Backup and wallet data; logs; a first smoke test; where the libraries have run. The README and
  chapter 8 follow.

### Tested

- On the Android emulator (`x86_64`), with this release's AAR in debug builds (CheckJNI on), on
  API 24 and on API 35 with 16 KB pages: the offline wallet; a wallet restored from its mnemonic
  and synced against a mainnet node, then opened again from its bytes with a connection without
  credentials; `ZephyrDaemonRpc` on API 35, and on API 24 with core library desugaring; without
  desugaring, the new error on API 24. The ARM libraries have not yet run on hardware.

### The dependency

Android: `io.github.loki-notfound:zephyr-java-android:2.3.0-sec.2-java.3`; the JVM:
`io.github.loki-notfound:zephyr-java:2.3.0-sec.2-java.3`. The repository line, the `packaging`
line and the dependencies are those of the earlier releases; guide chapter 6 says where they go.

### Verification

```sh
gpg --import KEYS.asc
git verify-tag v2.3.0-sec.2-java.3
gpg --verify SHA256SUMS.asc && sha256sum -c SHA256SUMS
```

## v2.3.0-sec.2-java.2

The second release on the same core as `v2.3.0-sec.2-java.1`: a fix for the Android `x86_64`
library, a helper for the asset of an output, and build changes. No breaking change.

### Built from

The same zephyr-backports release set of 2026-11-07 as the previous release, unchanged:

| | Tag | Commit | Tag subject |
|---|---|---|---|
| zephyr | `v2.3.0-sec.2` | `8c6a120256b4bd887d6e7167d929d76e3507e0e5` | zephyr v2.3.0 + 30 security backports from Monero |
| zephyr-cpp | `v2.3.0-sec.1` | `3a2ee602ee28e6b7941b617ad4706349ad992641` | zephyr-cpp master (4a99272f6d7a171cf6625016939094637459975a) + 4 security backports from monero-cpp |

### Fixed

- The `x86_64` library in the AAR no longer needs an executable stack: every native library is
  now linked with `-z noexecstack` (the core's assembly files carry no non-executable-stack note,
  and NDK r18b's linker marked that library as needing one).

### Added

- `ZephyrWallet.getOutputAssetType(output)`: the asset of an output from `getOutputs()`, which
  the core does not report, read from the output's transaction in the wallet's history. Guide
  chapter 8 has the rule and its limits.
- Javadoc starting "Zephyr:" on `checkTxKey`, `checkTxProof`, `ZephyrCheckTx.getReceivedAmount()`,
  `getOutputs()` and `ZephyrOutputWallet.getAssetType()`: the amount of a payment check is 0 and
  an output carries no asset in the full wallet, why, and the workaround.

### Build

- The developer build (`bin/build_libzephyr_java.sh`) compiles for the generic CPU instead of the
  build machine's, so a library or a compiler cache made on one machine runs on another.
- The Maven repository on Pages keeps every release: this release's site carries
  `2.3.0-sec.2-java.1` next to `2.3.0-sec.2-java.2`, and each `maven-metadata.xml` lists both.

### The dependency

Android: `io.github.loki-notfound:zephyr-java-android:2.3.0-sec.2-java.2`; the JVM:
`io.github.loki-notfound:zephyr-java:2.3.0-sec.2-java.2`. The repository line, the `packaging`
line and the dependencies are those of the previous release, below.

### Verification

```sh
gpg --import KEYS.asc
git verify-tag v2.3.0-sec.2-java.2
gpg --verify SHA256SUMS.asc && sha256sum -c SHA256SUMS
```

## v2.3.0-sec.2-java.1

The first release of zephyr-java: Java bindings for the Zephyr wallet core (`zephyr-cpp` over
Zephyr's `wallet2`), derived from monero-java at commit `53a54543` (2023-07-10), for Android (AAR)
and for desktop and server JVMs (JAR). The native core is linked statically into one library per
platform, `libzephyr-java.so`, which exports only its JNI entry points.

### Built from

The core is built only from the tagged commits of the zephyr-backports forks:

| | Tag | Commit | Tag subject |
|---|---|---|---|
| zephyr | `v2.3.0-sec.2` | `8c6a120256b4bd887d6e7167d929d76e3507e0e5` | zephyr v2.3.0 + 30 security backports from Monero |
| zephyr-cpp | `v2.3.0-sec.1` | `3a2ee602ee28e6b7941b617ad4706349ad992641` | zephyr-cpp master (4a99272f6d7a171cf6625016939094637459975a) + 4 security backports from monero-cpp |

These two tags belong to the zephyr-backports **release set of 2026-11-07**, which also names
zephyr-javascript `v2.3.0-sec.1` (`7b17a14280bd84ca783287f4640b3c744d25319f`) and zephyr-wallet
`v2.3.0-sec.1` (`edef8c0ee281024de1a718aa6dce39bce75f3687`); zephyr-java does not build those two.
The backports number each fork on its own: `sec.<N>` in this release's version is zephyr's tag
number, and the zephyr-cpp tag is named here and in `manifest.json` (`core.zephyr-cpp.tag`, next
to `core.release_set` and its members).

All tags are signed with the zephyr-backports release key
`FB39 94F0 997C A6F0 43D7  2491 9EFF 03F5 27D2 40A1`. A build against ZephyrProtocol's original
of the same release (zephyr `v2.3.0` = `67c5f53b878fef41fb5e74c4382d5b7a2f37fd8a`, zephyr-cpp
`4a99272f6d7a171cf6625016939094637459975a`) is expected to work as well.

### Platforms

- **Android** (`io.github.loki-notfound:zephyr-java-android`, AAR): minSdk 24; native libraries
  for `arm64-v8a`, `armeabi-v7a` and `x86_64`, 16 KB page aligned, stored uncompressed; consumer
  R8 rules included. `armeabi-v7a` needs a CPU with VFPv4. Built with NDK r18b through Zephyr's
  own `contrib/depends`. The AAR is built and checked (16 KB page alignment, uncompressed native
  libraries) in a throwaway consumer app; it has not yet run on a device or an emulator.
- **Linux** (`io.github.loki-notfound:zephyr-java`, JAR): the native libraries for `x86_64` and
  `arm64` inside the JAR, loaded from it. Built in a Debian 12 container (glibc 2.36 or newer:
  Debian 12, Ubuntu 23.04 and later, RHEL 10); everything else is linked statically. The `arm64`
  library is built and checked, not yet run.
- **macOS and Windows**: no packaged natives in this release.

### The dependency

Android (Gradle, Kotlin DSL):

```kotlin
repositories {
    maven { url = uri("https://loki-notfound.github.io/myzephyr-java/maven") }
}

android {
    packaging { resources.excludes += "/META-INF/DEPENDENCIES" }
}

dependencies {
    implementation("io.github.loki-notfound:zephyr-java-android:2.3.0-sec.2-java.1")
}
```

The `packaging` line is required in every app: three of the Apache HTTP client's jars carry a
`META-INF/DEPENDENCIES` file, and the app's resource merge fails on the duplicates. Both the AAR
and the JAR bring the same three dependencies: `jackson-core` and `jackson-databind` 2.13.5 (the
last Jackson line for Android below API 26) and `httpclient5` 5.2.1. `jeromq` (only for
`ZephyrWalletRpc`'s ZMQ listener) is optional and must be added by an application that uses it.
The JAR for Maven is `io.github.loki-notfound:zephyr-java:2.3.0-sec.2-java.1`; see the README's
"Coordinates".

### Breaking changes against monero-java

zephyr-java mirrors monero-java's API; a port changes the following. Guide chapter 7, "Coming from
monero-java", has the details and the full renaming table.

- **Packages and names:** `monero.*` → `zephyr.*`, `common.*` → `zephyr.lib.*`, classes
  `Monero*` → `Zephyr*`, the native library `monero-java` → `zephyr-java`.
- **`toAtomicUnits` / `fromAtomicUnits`:** `MoneroUtils.xmrToAtomicUnits(double)` is
  `ZephyrUtils.toAtomicUnits(double)`, `MoneroUtils.atomicUnitsToXmr(BigInteger)` is
  `ZephyrUtils.fromAtomicUnits(BigInteger)`.
- **Balances are per asset:** `getBalance()` returns a `ZephyrBalance`;
  `getBalance(Integer accountIdx)` returns `Map<Integer, ZephyrBalance>` keyed by subaddress;
  `getBalance(String assetType, Integer accountIdx[, Integer subaddressIdx])` returns a
  `BigInteger`. The unlocked balance has the same shapes; `ZephyrAccount` and `ZephyrSubaddress`
  hold a `ZephyrBalance`. `ZephyrBalance.equals` treats an absent asset as zero.
- **The listener names the asset:** `onBalancesChanged(BigInteger, BigInteger)` became
  `onBalancesChanged(BigInteger, BigInteger, String assetType)`; the old method is `final` and
  deprecated, so a ported listener that overrides it does not compile. Outputs carry the asset in
  `getAssetType()`.
- **Every send names a currency pair:** a `ZephyrTxConfig` needs `setSourceCurrency` and
  `setDestinationCurrency`, a plain transfer included; without them the core fails with "Must
  specify source currency". The compiler does not find this one.
- **Addresses and constants:** Zephyr's address prefixes (multi-byte varints) and network codes in
  `ZephyrNetworkType`; `ZephyrUtils.RING_SIZE` is 16. Bouncy Castle and commons-codec are no
  longer dependencies (own Keccak-256 and hex).
- **The RPC classes are a first pass:** `ZephyrWalletRpc` serves the base coin and rejects the
  per-asset calls, reserve info and supply with a clear message.
- **Dependencies:** `slf4j-simple` is no longer a runtime dependency (test scope only), and
  `jeromq` is optional.

New in zephyr-java: `getReserveInfo()` and `getCirculatingSupply()` on the wallet, `ZephyrAssets`
with the asset names, and the pricing record on daemon block headers.

### Tested

The full wallet ran on mainnet against a node with real funds: create, open, sync, balances per
asset, transfers, a ZPH → ZRS conversion and sweeps (five transactions, verified on the node).
The pure test suite has 30 tests. Conversions other than ZPH → ZRS are not tested on mainnet.

### Known limitations

Guide chapter 8, [Known limitations](https://github.com/loki-notfound/myzephyr-java/blob/v2.3.0-sec.2-java.1/docs/guide/08-known-limitations.md), lists them with their
status. The ones a wallet app meets first: `getOutputs()` reports no asset; the asset of an
unconfirmed output notification is empty; `checkTxKey` returns amount 0; on Android, TLS to a node
needs the app's help (the native wallet does not use Android's certificate store).

### Known issue

The `x86_64` library in the AAR (`jni/x86_64/libzephyr-java.so`) is marked as needing an
executable stack: NDK r18b's linker does that for the core's assembly files, which carry no
non-executable-stack note. Android may refuse to load it, or it weakens the hardening of the app on
`x86_64` devices and emulators; the other libraries are not affected, and the next release fixes it.
Fixed in v2.3.0-sec.2-java.2.

### Verification

The release tag and `SHA256SUMS` are signed with the maintainer key
`31DA 39AB 43E0 CA67 F1F3  A661 BA2A FF22 DF8A 4A13`; its public key is `KEYS.asc` in the
repository.

```sh
gpg --import KEYS.asc
git verify-tag v2.3.0-sec.2-java.1
gpg --verify SHA256SUMS.asc && sha256sum -c SHA256SUMS
```

The Maven repository and the site are the archive `site-<version>.tar.gz`, whose hash is in
`SHA256SUMS`; Pages serves its contents.

### Build manifest

`manifest.json` among the release files records the zephyr-java commit and its
`SOURCE_DATE_EPOCH`, both core pins (tag, commit, URL), the build-layer patches, the toolchain
(compilers, NDK, CMake, JDK, Maven, Gradle and the Android Gradle plugin, the Linux container
image and its glibc), every depends package with its sha256, and the size and sha256 of every
release file. Every artifact is built twice in the same environment, and the hashes must match
before `SHA256SUMS` is signed. Guide chapter 9 explains how to repeat the build.
