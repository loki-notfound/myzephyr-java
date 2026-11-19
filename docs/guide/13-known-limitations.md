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
| the process can hang in its exit while background syncing refreshes | observed on 2026-11-18, 20 of 20 runs; open; close every wallet first | [Close every wallet before the process ends](04-threads-and-listeners.md#close-every-wallet-before-the-process-ends) |
| `close()` can wait up to the core's timeout of 3 min 30 s for a node request; read in the code, also up to a sync period, or for a relay that hangs when it meets the end of a sync on another thread | the timeout observed on 2026-11-19; the other two read in the code, not seen | [Closing a wallet that syncs](04-threads-and-listeners.md#closing-a-wallet-that-syncs) |

## zephyr-java

| Limitation | Status | Details |
|---|---|---|
| `close()` has no time limit: it waits for the calls in flight and the core's sync thread | by design in `2.3.0-sec.2-java.5` | [Closing a wallet that syncs](04-threads-and-listeners.md#closing-a-wallet-that-syncs) |
| inside a listener callback, the calls that sync, scan, relay, import, save or close the wallet are refused with `ZephyrError` | by design in `2.3.0-sec.2-java.5` | [What a callback may call](04-threads-and-listeners.md#what-a-callback-may-call) |
| callbacks of two threads can run at the same time; a listener must be thread-safe | by design in `2.3.0-sec.2-java.5` | [Callbacks run on threads inside the core's calls](04-threads-and-listeners.md#callbacks-run-on-threads-inside-the-cores-calls) |
| `onOutputReceived` and `onOutputSpent` repeat at confirmation and unlock; the spent notification at unlock is a summary | by design, in the Javadoc and the core's code | [The five callbacks](04-threads-and-listeners.md#the-five-callbacks) |
| calls from several threads on one wallet, apart from `close()`, are not known to be safe | not established | [Calls from several threads](04-threads-and-listeners.md#calls-from-several-threads) |
| an invalid account or subaddress index may end the process: some native calls do not catch the core's failure | read in the code; not run | [What is not a ZephyrError](09-errors.md#what-is-not-a-zephyrerror) |
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

- **The offline tests of the library**, 71 at the last code change before the guide was revised.
  They call every public method of a closed wallet and the calls refused inside a callback. They
  also cover a failed save at close, `close()` racing calls on another thread, the amounts table
  and the library's logging with planted secrets.
- **The closing tests in child processes**, each of which fails on a crash, a hang or a thread
  left attached. They cover `close()` during a foreground `sync()` (10 of 10 runs), during busy
  background syncing, right after `startSyncing()` and `stopSyncing()`, and from a callback. Also
  `close(true)` while a listener held the refresh, a `sync()` from a callback, and listeners added
  and removed in a loop during background syncing.
- **The read-only integration tests** against the node, and the TLS tests against a node with a
  publicly signed certificate.
- All of these again under `-Xcheck:jni`, without a warning or fatal error from the JNI checks.
- The offline tests and the closing tests also ran on 2026-11-18 on the Linux `x86_64` library
  built in the release container, without Trezor and protobuf.
- **A minified Android app** (AGP 9.5, R8's full mode) was built against an AAR of these sources
  on 2026-11-19; it was not run. A JVM program with Gradle resolved the JAR from a local copy of
  the Maven Central bundle and ran it.

**Not on Android.** For `2.3.0-sec.2-java.5` nothing had run on Android or on the emulator when
this section was written (2026-11-19).

### Runs of earlier releases

These were made with earlier releases and not repeated for `2.3.0-sec.2-java.5`. The core they ran
is the same in `java.5`; the Java layer and the JNI bridge around it changed:

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

- anything of `2.3.0-sec.2-java.5` on Android, among it the closing and listener changes on ART
  with CheckJNI, and the minified app on a device;
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
were observed or read:

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
- `close(true)` did not catch a failing save in native code (read in the code). Now the wallet is
  closed and freed, and `close(true)` throws.
- At `FINE` and from `setLogLevel(2)` the library logged transaction keys, passwords and
  mnemonics, and `ZephyrRpcError.toString()` printed the request parameters, passwords included.
  Now the library logs no transaction config or key, and redacts the secret values.
- `ZephyrUtils.toAtomicUnits(double)` threw below 0.001 and from 10^7, and gave about 8 % too much
  for values printed with 19 or more decimals.
- No call reported whether a background scan still ran, so a safe `close()` was not possible.
  Now `close()` waits for the core's sync thread by itself.
- A minified Android app failed to build in R8 unless it added its own `-dontwarn` rules
  (observed with the `java.4` AAR on 2026-11-19). The AAR now brings them.
- The RPC classes were a first pass in which `ZephyrWalletRpc` served the base coin only. The class
  was removed ([The wallet RPC client was removed](11-the-rpc-classes.md#the-wallet-rpc-client-was-removed)).
