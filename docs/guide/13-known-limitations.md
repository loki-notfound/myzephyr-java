# 13. Known limitations

These are the limits a wallet app runs into with `2.3.0-sec.2-java.5`. Most come from the Zephyr
core (zephyr-cpp and `wallet2`), which zephyr-java builds unchanged. The backports change the core
only for security fixes, so functional gaps stay until the core itself changes. Each row says what
happens, its status, and where the details are. The rest of the guide explains each one where its
topic is.

Status words:

- *core*: behaviour of the Zephyr core;
- *open*: not fixed;
- *by design*: the library does it on purpose;
- *observed*: the run that showed it, with its date;
- *read in the code*: found in the source, not in a run;
- *not tested*: nobody ran it against Zephyr.

The defects that `2.3.0-sec.2-java.5` fixed are listed at the end
([Fixed in 2.3.0-sec.2-java.5](#fixed-in-230-sec2-java5)).

## The core

| Limitation | Status | Details |
|---|---|---|
| `checkTxKey` and `checkTxProof` return amount 0 for every current transaction, and `checkTxKey` reports `good` whenever it does not throw: it cannot confirm a payment | core, open; the amount confirmed on mainnet on 2026-11-07, `good` read in the code | [checkTxKey returns amount 0](06-history.md#checktxkey-returns-amount-0) |
| `getOutputs()` reports no asset | core, open; `getOutputAssetType` from `java.2` takes it from the transaction | [getOutputs() reports no asset](06-history.md#getoutputs-reports-no-asset) |
| `getOutputAssetType` reads a split conversion to one's own account as the source asset | core, open | [getOutputs() reports no asset](06-history.md#getoutputs-reports-no-asset) |
| the asset of an output notification in the pool is empty | core, stays; observed on mainnet on 2026-11-07 | [Outputs in the pool carry no asset](04-threads-and-listeners.md#outputs-in-the-pool-carry-no-asset) |
| `changePassword` fails for a wallet opened from bytes | core; confirmed on mainnet | [The password](02-wallets.md#the-password) |
| `save()` and `close(true)` write nothing for a wallet without a path, and report no error | core, read in the code | [Wallet bytes](02-wallets.md#wallet-bytes) |
| each failed open, such as one with a wrong password, leaks the half-built wallet in the core | core, read in the code | [Opening wallet files](02-wallets.md#opening-wallet-files) |
| `sweepOutput` validates with `ZEPH` and is most likely unusable | core, read in the code; not tested | [Sweeping](07-sending.md#sweeping) |
| `sweepUnlocked` without an account index sweeps every account with an unlocked balance in the source asset | core, read in the code | [Sweeping](07-sending.md#sweeping) |
| a payment ID is always refused, `setFee` is never read, and `setSweepEachSubaddress(true)` has no effect | core, read in the code | [Fees and priorities](07-sending.md#fees-and-priorities), [Sweeping](07-sending.md#sweeping) |
| destinations on the transactions that `createTxs` and `sweepUnlocked` return report the asset `"ZEPH"` | core, read in the code | [Conversions in the history](06-history.md#conversions-in-the-history) |
| every node is treated as trusted (monero's trusted-daemon mode), and its error text can reach exception messages | core, read in the code | [A remote node](03-connecting-to-a-node.md#a-remote-node) |
| `sync()` against a node that is still syncing returns 0 blocks without an error | core, read in the code; not run | [Syncing](03-connecting-to-a-node.md#syncing) |
| background syncing reports no errors to the app | core, read in the code | [Background sync](03-connecting-to-a-node.md#background-sync) |
| a conversion's pricing record must be at most 10 blocks old when the chain includes it | network rule, read in the code | [Building a conversion](08-conversions-and-the-reserve.md#building-a-conversion) |
| testnet and stagenet never reach the V2 rules | core network tables | [Network types](02-wallets.md#network-types) |
| no certificate pinning; the host name must match; the Network Security Configuration does not apply | needs the core | [What TLS cannot do](03-connecting-to-a-node.md#what-tls-cannot-do) |
| the core's listener set is not synchronised: the wallet's first listener, added while a sync runs on another thread, races the core's sync thread | core, read in the code; not provoked | [Add the first listener before startSyncing()](04-threads-and-listeners.md#add-the-first-listener-before-startsyncing) |
| once a listener was added, the core does its per-block listener work for the rest of the wallet's life | core; about 11 % per scan, measured on 2026-11-18 | [Add the first listener before startSyncing()](04-threads-and-listeners.md#add-the-first-listener-before-startsyncing) |
| `save()`, `moveTo()`, `changePassword()` and `getData()` are not exclusive with background syncing, and no call reports when the core's sync thread has finished after `stopSyncing()` | core, read in the code; `close(true)` is exclusive | [Calls from several threads](04-threads-and-listeners.md#calls-from-several-threads) |
| `setDaemonConnection` while a refresh runs on another thread resets fields the refresh uses without a lock | core, read in the code | [Calls from several threads](04-threads-and-listeners.md#calls-from-several-threads) |
| the process can hang in its exit while background syncing refreshes | observed on Linux on 2026-11-18, 20 of 20 runs, with a development build from before the later closing fixes; not repeated on the final code; open; close every wallet first | [Close every wallet before the process ends](04-threads-and-listeners.md#close-every-wallet-before-the-process-ends) |
| `close()` can wait up to the core's timeout of 3 min 30 s for a node request; read in the code, also up to a sync period, or for a relay that hangs when it meets the end of a sync on another thread | the timeout observed on 2026-11-19; the other two read in the code, not seen | [Closing a wallet that syncs](04-threads-and-listeners.md#closing-a-wallet-that-syncs) |

## zephyr-java

| Limitation | Status | Details |
|---|---|---|
| `close()` has no time limit: it waits for the calls in flight and the core's sync thread | by design in `2.3.0-sec.2-java.5` | [Closing a wallet that syncs](04-threads-and-listeners.md#closing-a-wallet-that-syncs) |
| inside a listener callback, the calls that sync, scan, relay, import, save or close the wallet are refused with `ZephyrError` | by design in `2.3.0-sec.2-java.5` | [What a callback may call](04-threads-and-listeners.md#what-a-callback-may-call) |
| callbacks of two threads can run at the same time; a listener must be thread-safe | by design in `2.3.0-sec.2-java.5` | [Callbacks run on threads inside the core's calls](04-threads-and-listeners.md#callbacks-run-on-threads-inside-the-cores-calls) |
| `onOutputReceived` and `onOutputSpent` repeat at confirmation and unlock; the spent notification at unlock is a summary | by design, in the Javadoc and the core's code | [The five callbacks](04-threads-and-listeners.md#the-five-callbacks) |
| calls from several threads on one wallet, apart from `close()`, are not known to be safe | not established | [Calls from several threads](04-threads-and-listeners.md#calls-from-several-threads) |
| `ZephyrUtils.toAtomicUnits(double)` goes through a `double` and drops decimals beyond the twelfth silently | specified Java behaviour; run on 2026-11-18 | [Amounts](05-assets-and-balances.md#amounts) |
| `ZephyrDaemonRpc` needs API 26, or core library desugaring on API 24 and 25 | by design of the HTTP client | [The RPC classes below API 26](10-android.md#the-rpc-classes-below-api-26) |
| the areas inherited from monero-java listed in chapter 1 | not tested | [What zephyr-java does and does not do](01-getting-started.md#what-zephyr-java-does-and-does-not-do) |

## Wallets and their data

| Limitation | Status | Details |
|---|---|---|
| a view-only wallet's balance counts everything ever received once the wallet has spent | observed on 2026-11-17 | [View-only wallets](02-wallets.md#view-only-wallets) |
| the native log, when switched on, records the primary address, the wallet path, amounts and transaction hashes, and grows by a line per block with a listener | observed on 2026-11-17 on the desktop, the content also with the released JAR | [The native log](09-errors.md#the-native-log) |

## Platforms, building and packaging

| Limitation | Status | Details |
|---|---|---|
| no packaged native libraries for macOS and Windows | not packaged in `2.3.0-sec.2-java.5` | [The native library](01-getting-started.md#the-native-library) |
| the Linux JAR needs glibc 2.36 or newer | release natives built on Debian 12 | [Linux: the glibc floor](01-getting-started.md#linux-the-glibc-floor) |
| the `armeabi-v7a` library needs a CPU with VFPv4; there is no 32-bit `x86` library | build choice | [Devices](10-android.md#devices) |
| the Android natives are reproducible at the same build path only | NDK r18b | [Reproducibility](14-building-and-verifying.md#reproducibility) |

## What was tested

For `2.3.0-sec.2-java.5`, from 2026-11-18 to 2026-11-19, on Linux `x86_64` (Debian 13, OpenJDK
21), against a mainnet node, read-only. Nothing was sent or relayed. The runs used development
builds of the sources as they stood on those days:

- **The offline tests of the library**, 72 at the last code change. They call every public method
  of a closed wallet and the calls refused inside a callback. They also cover a failed save at
  close and `close()` racing calls on another thread. Nine failing cases in eight native calls
  each run in a child process. Six are failures of the core, one the bridge's own conversion of an
  amount, one a large conversion. The amounts table and the library's logging with planted secrets
  are covered as well.
- **The closing tests in child processes**, each of which fails on a crash, a hang or a thread
  left attached. They cover `close()` during a foreground `sync()` (10 of 10 runs), during busy
  background syncing, right after `startSyncing()` and `stopSyncing()`, and from a callback. Also
  `close(true)` while a listener held the refresh, a `sync()` from a callback, and listeners added
  and removed in a loop during background syncing.
- **The read-only integration tests** against the node, and the TLS tests against a node with a
  publicly signed certificate.
- All of these again under `-Xcheck:jni` on the final code, without a warning or fatal error from
  the JNI checks.
- On 2026-11-18 the offline and closing tests of the code as it stood then also ran on the
  Linux `x86_64` library built in the release container, without Trezor and protobuf. That was
  before the last closing fixes: 65 offline tests and 35 closing tests.
- A JVM program with Gradle resolved the JAR from a local copy of the Maven Central bundle of an
  earlier state of these sources and ran it. That state was still versioned `2.3.0-sec.2-java.4`,
  and the program resolved it under those coordinates.

**On the Android emulator**, on 2026-11-19 and 2026-11-20: the `x86_64` library, against the
same node, read-only, on five images. The API 36 image with 16 KB pages was used on 2026-11-20
only.

| Image | Pages | Build |
|---|---|---|
| API 24 | 4 KB | `NYC` |
| API 35, Google APIs | 16 KB | `AE3A.240806.043` |
| API 35 | 4 KB | `AE3A.240806.019` |
| API 36 | 4 KB | `BE2A.250530.026.D1` |
| API 36, Google APIs | 16 KB | `BE2A.250530.026.F3` |

- **The AAR of a release build of these sources**, with the release's native libraries and
  classes, ran on all five images. It ran in a minified app (AGP 9.5, R8's full mode) and in the
  same app built without R8, neither debuggable. Two closing scenarios ran 20 times per build and
  image, each in its own process. One is `close()` 1.5 s into busy background syncing, with no
  callback after it. In the other, a listener throws from its first `onSyncProgress` and
  `onNewBlock`. A second listener receives the same callbacks until the scan reaches the node's
  height. All 400 runs passed without a crash: 320 on 2026-11-19 and 80 on the API 36 16 KB image
  on 2026-11-20. Each process had one callback thread of the core, which had ended after
  `close()`, and `close()` waited 3 to 999 ms.
- **The exit and refusal scenarios with that AAR**, on 2026-11-20, in the minified app, on API 24,
  API 35 and API 36 with 4 KB pages and API 36 with 16 KB pages. Three times per image a process
  was left with a wallet syncing in the background; it stayed alive until `am force-stop` ended
  it. Three times per image `System.exit(0)` with a wallet syncing ended the process with exit
  code 0. Once per image ran each of these: `close()` from a callback, which was refused;
  `stopSyncing()` directly followed by `close()`; and `save()`, `getData()`, `sync()`,
  `startSyncing()` and `close()` from a callback, each refused. All 36 runs went as expected,
  without a crash or a tombstone. In the three scenarios run once, `close()` waited 5 to
  1,100 ms, and each process had one callback thread of the core. Nothing else ran with this AAR,
  and nothing under CheckJNI.
- **A short check of an AAR of a later release build**, on 2026-11-20, on all five images. That
  AAR has the release's consumer rules, native libraries and classes. In the minified app it
  passed the app's own checks: an offline wallet, a sync with a listener and `ZephyrDaemonRpc` (on
  API 24 the documented `java.time` error). Then ten runs per image, each in its own process.
  `close()` during a foreground `sync()` ran three times. Once each ran `close()` during busy
  background syncing, a throwing listener, `stopSyncing()` directly followed by `close()`,
  `close()` from a callback and the other refused calls from a callback. Once each, too, a process
  was left with a wallet syncing until `am force-stop` ended it, and `System.exit(0)` with a
  wallet syncing ended its process. All 50 runs went as expected, without a crash of the app. That
  includes the API 35 16 KB image, whose own processes crashed at boot again. `close()` waited 4
  to 1,195 ms, and once 9,747 ms: during busy background syncing on the API 36 16 KB image, while
  the callbacks did not advance. On API 24 and on the API 36 16 KB image a debug app ran under
  CheckJNI without a JNI error. It ran an offline wallet, a read-only sync and a reopen from bytes,
  `ZephyrDaemonRpc` (on API 24 the `java.time` error) and TLS. On the API 36 16 KB image the
  system logged one ANR for the test app during a run that passed; its cause was not established.
- **AARs of these sources before the last change to the JNI bridge**, which attached the core's
  thread for each callback. Debug apps under ART's CheckJNI ran the closing tests as instrumented
  tests on API 24 and on the API 35 16 KB image. All 19 passed on each image. They cover `close()`
  during a foreground `sync()` (5 of 5 runs per image), during busy background syncing and right
  after `stopSyncing()`. Also a throwing listener, `close()` and the refused calls from a
  callback, and a process ended with a wallet syncing. Every callback thread of the core had ended
  after `close()`. They also ran an offline wallet, a sync and a reopen from bytes,
  `ZephyrDaemonRpc` and TLS to a node with a publicly signed certificate.
- **The minified app with these earlier AARs.** With the consumer rules of the release it ran on
  API 24 and on the API 35 16 KB image. It read an offline wallet's accounts, subaddresses,
  address book, outputs and balance. It synced with a listener and read the node with
  `ZephyrDaemonRpc` (on API 24 it got the documented `java.time` error). With rules from before
  the interceptor rule, it ran the closing scenarios from its own code under CheckJNI. They passed
  19 of 19 on API 24 and 17 of 19 on the API 35 16 KB image.
- **The two-scenario matrix with an earlier AAR**, in the minified app and in the app without R8.
  It ran on the first four images on 2026-11-19 and on the API 36 16 KB image on 2026-11-20.

**The crashes on the API 35 16 KB image.** With the earlier AARs, apps that are not debuggable
crashed with `SIGSEGV` on this image, during background syncing. That happened in 2 of 19 runs of
the closing scenarios, 3 of 24 repeated runs and 5 of 80 runs of the matrix. Each crash was on a
callback thread of the core or in ART's garbage collector. Debug apps on this image did not crash,
in 27 runs. Processes of this image without zephyr-java code also crashed with `SIGSEGV`, at boot
before the app was installed and during the runs. In an idle window as long as the runs, with
nothing of zephyr-java running, one of its processes crashed with `SIGILL` in browser code.
During the matrix no process crashed on the three 4 KB images, with either AAR, ours or the
image's own. In the matrix with the AAR that has the release's native libraries this image had
no crash of zephyr-java in 80 runs, and its own processes still crashed.

**The API 36 16 KB image.** With the earlier AAR the matrix had no crash there, in 80 runs, with
600 to 10,000 callback threads per process. With the AAR that has the release's native libraries
the matrix had none in 80 runs either.
None of the image's own processes crashed during the runs or in an idle window as long as them.
At one of its four boots its Bluetooth process ended with `SIGABRT`, before the idle window began.

No run under CheckJNI showed a JNI error in a process of zephyr-java.

### Runs of earlier releases

These were made with earlier releases and not repeated for `2.3.0-sec.2-java.5`. The core's
sources are the same in `java.5`, which builds them without Trezor; the Java layer and the JNI
bridge around the core changed:

- **On mainnet with real funds, on the desktop** (2026-11-06 to 2026-11-10): create, open, sync,
  balances per asset, transfers, a ZPH → ZRS conversion and sweeps. Five transactions were
  verified on the node. Conversions other than ZPH → ZRS were not run
  ([What was tested](08-conversions-and-the-reserve.md#what-was-tested)).
- **On Linux `x86_64` on 2026-11-17**, with the `2.3.0-sec.2-java.4` sources: the messages of
  `createTxs`, the native log, a view-only wallet, `moveTo` and the heights.
- **The coexistence with monero-java** in one JVM
  ([Both libraries in one program](12-coming-from-monero-java.md#both-libraries-in-one-program)).
- **On the Android emulator**, as the release check of `2.3.0-sec.2-java.4`: the `x86_64` library
  on API 24 and on API 35 with 16 KB pages, in debug builds (so under ART's CheckJNI). There the
  native library loaded and an offline wallet worked. A wallet restored from its mnemonic synced
  against a mainnet node and opened again from its bytes, and `ZephyrDaemonRpc` read the node (on
  API 24 with desugaring). `https://` to a mainnet node with a publicly signed certificate worked
  without setup. With the system store alone it worked on API 35 and failed on API 24, which lacks
  ISRG Root X1.

The `arm64-v8a` and `armeabi-v7a` libraries are built and checked the same way (exports, page
alignment, packaging). They have not run on hardware.

## What was not tested

- listener callbacks and the closing tests with the bridge of `2.3.0-sec.2-java.5` under ART's
  CheckJNI: the debug app's tests that ran this bridge add no listener;
- a minified app with a transaction history, or with R8 settings other than AGP 9.5's full mode;
- the conversions other than ZPH → ZRS on mainnet;
- the ARM libraries for Android on hardware;
- the Linux `arm64` library, which is built and checked but has not been run;
- `configureNativeLogging` on Android;
- the areas inherited from monero-java listed in
  [What zephyr-java does and does not do](01-getting-started.md#what-zephyr-java-does-and-does-not-do);
- a node that refuses a relayed transaction, and the message for funds that are not unlocked;
- a large history, and a first sync over the internet from an old restore height;
- the publication on Maven Central itself (the bundle was built and checked locally).

## Fixed in 2.3.0-sec.2-java.5

These limitations of `2.3.0-sec.2-java.4` are gone. The conditions are those under which they
were observed or read. The release notes list every fix:

- `close()` from another thread during `sync()` crashed the process (4 of 4 runs with the released
  `java.4` JAR, 2026-11-17). Now `close()` waits, and the sync throws "Wallet is closed".
- `close()` while background syncing scanned left a native thread attached, and the JVM did not
  exit, also with `stopSyncing()` right before (3 of 3 and 2 of 2 runs, 2026-11-17).
- An exception from a callback during background syncing left a native thread attached and the
  JVM did not exit (3 of 3 runs, 2026-11-17). During `sync()` it was printed and skipped the rest
  of that block's notifications. Now each listener's exception is caught and logged.
- Adding and removing listeners while background syncing ran could crash the process: the change
  raced the core's sync thread in the core's listener set. A loop that did so crashed a
  development build with `java.4`'s pattern in 8 of 8 runs (2026-11-18); `java.4` itself was not
  run.
- A wrong password in `openWallet` threw `java.lang.Exception`, and many calls let the core's
  `java.lang.Exception` through. Now every call of `ZephyrWalletFull` and `ZephyrUtils` throws
  `ZephyrError`.
- A `null` attribute key or value aborted the process (read in the bridge's code, observed on
  2026-11-18). Now it is refused.
- Some native calls did not catch the core's failure, which then ended the process: among them
  the account and subaddress calls, `getAddress` and the key getters. Among the cases that ended
  the process were `createSubaddress` with an account index out of range and a binary block that
  does not parse. So did a transaction config with an amount of 2^64 or more. That was observed on
  2026-11-19 with a development build whose bridge was the same in those places.
  `getReserveProofAccount` with such an amount ended it in the bridge's own conversion. Now every
  native call turns these failures into a `ZephyrError`.
- `ZephyrUtils.mapToBinary` crashed the process for a result of more than about 1 MB (observed on
  2026-11-19 with the same build). Now it returns.
- `close(true)` saved while the core's sync thread could still be refreshing the wallet (observed
  on 2026-11-18 with a development build whose save order at close was `java.4`'s). It also did
  not catch a failing save in native code (read in the code). Now it saves after that thread has
  ended; when the save fails, the wallet is closed and freed and `close(true)` throws.
- `close()` from a callback of background syncing aborted the process ("trying joining itself",
  observed on 2026-11-18 with a development build whose closing code was `java.4`'s). Now it is
  refused.
- `getReserveProofWallet` on a closed wallet crashed the process (observed on 2026-11-18 with the
  same build), and `changePassword` had no check for a closed wallet. Now every call of a closed
  wallet throws "Wallet is closed".
- A wallet whose node connection failed right after it was opened or created was never freed. Now
  it is closed before the exception is thrown.
- A listener given to `sync(listener)` that removed itself made the sync throw "Listener is not
  registered with wallet" at its end. It no longer does.
- At `FINE` and from `setLogLevel(2)` the library logged transaction keys, passwords and
  mnemonics, and `ZephyrRpcError.toString()` printed the request parameters, passwords included.
  Now the library's Java logging logs no transaction config or key, and redacts the secret values.
  The core's log at trace level still holds the transaction config of `sweepOutput` and
  `getPaymentUri`, without a secret key ([The native log](09-errors.md#the-native-log)).
- `ZephyrUtils.toAtomicUnits(double)` threw below 0.001 and from 10^7, and gave about 8 % too much
  for values printed with 19 or more decimals.
- No call reported whether a background scan still ran, so a safe `close()` was not possible.
  Now `close()` waits for the core's sync thread by itself.
- A minified Android app failed to build in R8 unless it added its own `-dontwarn` rules
  (observed with the `java.4` AAR on 2026-11-19). With earlier states of `2.3.0-sec.2-java.5`, a
  minified app built with them then failed at run time. R8 dropped what Jackson reads by
  reflection and merged the HTTP client's interceptors (observed on the emulator on 2026-11-19).
  The AAR's rules now cover all of these.
- The RPC classes were a first pass in which `ZephyrWalletRpc` served the base coin only. The class
  was removed ([The wallet RPC client was removed](11-the-rpc-classes.md#the-wallet-rpc-client-was-removed)).
