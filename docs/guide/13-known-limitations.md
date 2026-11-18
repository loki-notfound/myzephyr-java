# 13. Known limitations

These are the limits a wallet app runs into with `2.3.0-sec.2-java.4`. Most come from the Zephyr
core (zephyr-cpp and `wallet2`), which zephyr-java builds unchanged. The backports change the core
only for security fixes, so functional gaps stay until the core itself changes. Each row says what
happens, its status, and where the details are. The rest of the guide explains each one where its
topic is.

Status words:

- *core*: behaviour of the Zephyr core;
- *open*: not fixed;
- *observed*: the run that showed it, with its date;
- *not tested*: nobody ran it against Zephyr.

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
| every node is treated as trusted (monero's trusted-daemon mode), and its error text can reach exception messages | core, read in the code | [A remote node](03-connecting-to-a-node.md#a-remote-node) |
| `sync()` against a node that is still syncing returns 0 blocks without an error | core, read in the code; not run | [Syncing](03-connecting-to-a-node.md#syncing) |
| background syncing reports no errors to the app | core, read in the code | [Background sync](03-connecting-to-a-node.md#background-sync) |
| a conversion's pricing record must be at most 10 blocks old when the chain includes it | network rule, read in the code | [Building a conversion](08-conversions-and-the-reserve.md#building-a-conversion) |
| testnet and stagenet never reach the V2 rules | core network tables | [Network types](02-wallets.md#network-types) |
| no certificate pinning; the host name must match; the Network Security Configuration does not apply | needs the core | [What TLS cannot do](03-connecting-to-a-node.md#what-tls-cannot-do) |

## zephyr-java

| Limitation | Status | Details |
|---|---|---|
| `close()` from another thread during `sync()` crashes the process | observed on 2026-11-17 with the released JAR, 4 of 4 runs; open | [Closing a wallet that syncs](04-threads-and-listeners.md#closing-a-wallet-that-syncs) |
| `close()` while background syncing scans leaves a native thread attached; the JVM does not exit, also with `stopSyncing()` right before | observed on 2026-11-17 with the released JAR, 3 of 3 and 2 of 2 runs; open | [Closing a wallet that syncs](04-threads-and-listeners.md#closing-a-wallet-that-syncs) |
| no call reports whether a background scan is still running; `stopSyncing()` lets the batch in progress finish | read in the code; open | [Closing a wallet that syncs](04-threads-and-listeners.md#closing-a-wallet-that-syncs) |
| `onOutputReceived` and `onOutputSpent` repeat at confirmation and unlock; the spent notification at unlock is a summary | by design, in the Javadoc and the core's code | [The five callbacks](04-threads-and-listeners.md#the-five-callbacks) |
| an exception from a callback during background syncing leaves a native thread attached; the JVM does not exit | observed on 2026-11-17 with the released JAR, 3 of 3 runs; open | [An exception thrown in a callback](04-threads-and-listeners.md#an-exception-thrown-in-a-callback) |
| an exception from a callback during `sync()` is printed and swallowed, and skips the rest of that block's notifications | observed on 2026-11-17 | [An exception thrown in a callback](04-threads-and-listeners.md#an-exception-thrown-in-a-callback) |
| calls from several threads on one wallet are not known to be safe | not established | [Calls from several threads](04-threads-and-listeners.md#calls-from-several-threads) |
| a wrong password in `openWallet` throws `java.lang.Exception`, not `ZephyrError` | observed on 2026-11-17, also with the released JAR; open | [Where messages come from](09-errors.md#where-messages-come-from) |
| many wallet calls, among them the balance calls, `save` and the account calls, let the core's `java.lang.Exception` through | read in the source; open | [Calls that let the core's exception through](09-errors.md#calls-that-let-the-cores-exception-through) |
| an invalid account or subaddress index may end the process: some native calls do not catch the core's failure | read in the code; not run | [Calls that let the core's exception through](09-errors.md#calls-that-let-the-cores-exception-through) |
| `close(true)` does not catch a failing save in native code | read in the code; not run | [Closing](02-wallets.md#closing) |
| at `FINE` and from `setLogLevel(2)` the library logs transaction keys, passwords and mnemonics | read in the source | [Logging safely](09-errors.md#logging-safely) |
| `ZephyrUtils.toAtomicUnits(double)` throws below 0.001 and from 10^7, and truncates silently | specified Java behaviour; run on 2026-11-17 | [Amounts](05-assets-and-balances.md#amounts) |
| `ZephyrRpcError.toString()` prints the request parameters, which can hold passwords | open in `2.3.0-sec.2-java.4`; later releases redact them | [Logging safely](09-errors.md#logging-safely) |
| the RPC classes are a first pass: `ZephyrWalletRpc` serves the base coin only | planned work | [ZephyrWalletRpc](11-the-rpc-classes.md#zephyrwalletrpc) |
| the RPC classes need API 26, or core library desugaring on API 24 and 25 | by design of the HTTP client | [The RPC classes below API 26](10-android.md#the-rpc-classes-below-api-26) |
| the areas inherited from monero-java listed in chapter 1 | not tested | [What zephyr-java does and does not do](01-getting-started.md#what-zephyr-java-does-and-does-not-do) |

## Wallets and their data

| Limitation | Status | Details |
|---|---|---|
| a view-only wallet's balance counts everything ever received once the wallet has spent | observed on 2026-11-17 | [View-only wallets](02-wallets.md#view-only-wallets) |
| the native log, when switched on, records the primary address, the wallet path, amounts and transaction hashes, and grows by a line per block with a listener | observed on 2026-11-17 on the desktop, the content also with the released JAR | [The native log](09-errors.md#the-native-log) |

## Platforms, building and packaging

| Limitation | Status | Details |
|---|---|---|
| no packaged native libraries for macOS and Windows | not packaged in `2.3.0-sec.2-java.4` | [The native library](01-getting-started.md#the-native-library) |
| the Linux JAR needs glibc 2.36 or newer | release natives built on Debian 12 | [Linux: the glibc floor](01-getting-started.md#linux-the-glibc-floor) |
| the `armeabi-v7a` library needs a CPU with VFPv4; there is no 32-bit `x86` library | build choice | [Devices](10-android.md#devices) |
| the Android natives are reproducible at the same build path only | NDK r18b | [Reproducibility](14-building-and-verifying.md#reproducibility) |

## What was tested

- **On mainnet with real funds, on the desktop:** create, open, sync, balances per asset,
  transfers, a ZPH → ZRS conversion and sweeps. Five transactions were verified on the node.
  Conversions other
  than ZPH → ZRS were not run ([What was tested](08-conversions-and-the-reserve.md#what-was-tested)).
- **On the Android emulator:** see below.
- **On Linux `x86_64` on 2026-11-17**, with a developer build of the `2.3.0-sec.2-java.4` sources:
  listener exceptions, closing during a sync and reads from several threads. Also the messages of
  `createTxs`, the native log, a view-only wallet, `moveTo`, the heights and the amount helpers.
  The closing runs, the listener exceptions under background syncing, the wrong password and the
  native log's content were repeated that day with the released JAR.
- **The coexistence with monero-java** in one JVM
  ([Both libraries in one program](12-coming-from-monero-java.md#both-libraries-in-one-program)).

**Where it has run.** The `x86_64` library ran on the Android emulator as a release check. It ran
on API 24 and on API 35 with 16 KB pages, in debug builds (so under ART's CheckJNI). There:

- the native library loads, and an offline wallet works;
- a wallet restored from its mnemonic syncs against a mainnet node and opens again from its bytes;
- `ZephyrDaemonRpc` reads the node (on API 24 with desugaring);
- `https://` to a mainnet node with a publicly signed certificate works without setup (the shipped
  root certificates), on API 24 and on API 35 with 16 KB pages;
- with the system store alone it works on API 35 and fails on API 24, which lacks ISRG Root X1.

The `arm64-v8a` and `armeabi-v7a` libraries are built and checked the same way (exports, page
alignment, packaging). They had not run on hardware by release `2.3.0-sec.2-java.4` (2026-11-10).

## What was not tested

- the conversions other than ZPH → ZRS on mainnet;
- the ARM libraries for Android on hardware;
- the Linux `arm64` library, which is built and checked but has not been run;
- any of the observations of 2026-11-17 on Android, and `configureNativeLogging` on Android;
- the areas inherited from monero-java listed in
  [What zephyr-java does and does not do](01-getting-started.md#what-zephyr-java-does-and-does-not-do);
- a node that refuses a relayed transaction, and the message for funds that are not unlocked;
- a large history, and a first sync over the internet from an old restore height.
