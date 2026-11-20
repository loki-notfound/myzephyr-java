# 4. Threads and listeners

Wallet calls block, and listener callbacks come from the core. This chapter covers the threads
both run on, the five callbacks and what a callback may call. It then covers exceptions in
callbacks, closing a wallet that syncs, calls from several threads and the end of the process. It
ends with what is not known and with the rules for releases up to `2.3.0-sec.2-java.4`.

The blocks marked "Java, compiled" in this chapter come from
`src/test/java/examples/GuideThreads.java`. Statements marked "observed on 2026-11-18" or
"2026-11-19", and measurements of those days, come from runs on Linux `x86_64`. They used
development builds of the `2.3.0-sec.2-java.5` sources as they stood on those days. None of those
runs was made on Android; the statements that name the Android emulator say so. Statements about
`2.3.0-sec.2-java.4` name that release
([Where the statements come from](README.md#where-the-statements-come-from)).

## Calls block

`sync()`, `createTxs()`, `getTxs()` and the other wallet methods do their work on the calling
thread and return when the core is done. A first sync of a restored wallet can take minutes, and
building a transaction asks the node for decoy outputs. Never call them from a thread that must
stay responsive, such as Android's main thread. A sync or a send on the main thread freezes the
app. Use an executor or a coroutine dispatcher for I/O. The same holds for `close()`, which waits
for the calls in flight ([Closing a wallet that syncs](#closing-a-wallet-that-syncs)).

## Callbacks run on threads inside the core's calls

A callback runs on a thread that is inside a core call that notifies (read in the core's code).
That is the core's own background sync thread (`startSyncing()`), or a thread of yours inside
one of these calls:

- `sync()`, `rescanBlockchain()` or `scanTxs()`;
- `importMultisigHex()`, or `importKeyImages()` on a wallet connected to a node;
- a call that relays: `createTx`, `createTxs`, `sweepUnlocked`, `sweepOutput` and `sweepDust`
  with relay, `relayTx`, `relayTxs` and `submitTxs`.

`onNewBlock` and `onSyncProgress` come from a sync; the other calls deliver output and balance
callbacks. The core keeps its notifications in one queue without a thread of its own. Each of
these threads runs queued notifications while it waits for its own. So a callback can run on
another of these threads than the one whose work caused it.

**Callbacks of two threads can run at the same time.** In `2.3.0-sec.2-java.5` the JNI bridge
holds no lock while your listener runs. Background syncing and a relaying call on another thread
can therefore deliver callbacks at once. A listener must be thread-safe.

The core's background thread is attached to the JVM once, as a daemon thread, on its first
callback. It is detached when it ends, before `close()` returns. A thread of yours that delivers
callbacks inside a call stays attached as it was. Do not rely on a thread's identity or name.
Hand the event over to your own thread (an executor, Android's main looper) and return quickly.

`addListener()` and `removeListener()` never deliver a callback themselves. A listener that is
added receives the notifications from the next one on. A listener that is removed receives none
once the callbacks already under way have returned.

## Keep callbacks short

During a sync, the core hands each new block's notifications to the queue and **waits for them
to finish** before it goes on. A listener that does slow work stalls the sync, and so does a
listener that waits for something. Keep callbacks short. Read the wallet from a callback if you
must, and queue everything else
([What a callback may call](#what-a-callback-may-call)):

```java examples/GuideThreads.java
ExecutorService events = Executors.newSingleThreadExecutor();
wallet.addListener(new ZephyrWalletListener() {
  @Override
  public void onBalancesChanged(BigInteger balance, BigInteger unlockedBalance, String assetType) {
    events.submit(() -> showBalance(assetType, balance, unlockedBalance));  // return at once
  }
});
```

`removeListener` removes a listener that `addListener` added. A listener given to
`sync(listener)` is added for that sync only and removed when it ends. The callbacks of
background syncing (`startSyncing()`) follow the same rules.
[Syncing](03-connecting-to-a-node.md#syncing) has the details of syncing.

## Add the first listener before startSyncing()

The wallet's first listener, from `addListener` or `sync(listener)`, adds the library's native
listener to the core's listeners. That happens once per wallet, and the native listener stays
there until `close()`. Every later addition or removal only switches whether callbacks reach
Java, without a call into the core.

The core's set of listeners is not synchronised, and the core's sync thread copies it several
times per block (read in the core's code). So add a wallet's first listener before
`startSyncing()`, and not from another thread during a `sync()`. Added while a sync runs, it
changes that set under the copy, once. This race is the core's; it was not provoked in a run.

**Once a listener was added, every scan costs more.** The core then does its listener work for
every block, a balance check over every asset, also while no Java listener is registered. The
callbacks stop in the native code before they reach Java. Measured on 2026-11-18 on a background
scan of 8,000 blocks, six runs each, on a wallet with few transactions:

| Wallet | Median time of the scan |
|---|---|
| no listener ever added | 6,067 ms (6,043 ms with `java.4`'s behaviour) |
| a listener added and removed before the scan | 6,744 ms, about 11 % more |

A wallet that never needs callbacks should not get a listener. A wallet with a large history was
not measured.

## The five callbacks

`ZephyrWalletListener` has five callbacks; override the ones you need:

| Callback | When | What it carries |
|---|---|---|
| `onSyncProgress(height, startHeight, endHeight, percentDone, message)` | once per fetched block during a sync | the block, the range, a fraction 0.0 to 1.0 |
| `onNewBlock(height)` | once per fetched block | the block's height |
| `onBalancesChanged(balance, unlockedBalance, assetType)` | once per asset whose balance changed | the new balance and unlocked balance of that asset |
| `onOutputReceived(output)` | an output for the wallet appeared in the pool, in a block, and again when it unlocked | amount, account, subaddress, tx hash, height, `assetType` |
| `onOutputSpent(output)` | an output of the wallet was spent in a block, and again when that transaction unlocked | the same fields, with the asset |

**The output callbacks repeat.** By its Javadoc, `onOutputReceived` comes three times for one
output (in the pool, confirmed, unlocked) and `onOutputSpent` twice (confirmed, unlocked). In the
core's code, the spent notification at unlock is one summary input whose amount is the outgoing
amount plus the fee, not the real inputs. Count each output once, keyed by transaction hash and
output, and do not add up the notifications.

`onBalancesChanged` names the asset. monero-java's two-argument version still exists but is
`final` and deprecated. A listener ported from monero-java that overrides it fails to compile
instead of silently never being called.

## Outputs in the pool carry no asset

**The asset of an unconfirmed output notification is empty.** `onOutputReceived` for a payment
seen in the pool arrives with `getAssetType()` equal to `""`. `wallet2`'s callback for pool
payments has no asset parameter, and zephyr-cpp passes an empty string. The notification at
confirmation goes through a callback that carries the asset (from the core's code; the mainnet
test run did not get to observe it).

Use `onBalancesChanged` (which names the asset) or `getTxs()` (which reports the right asset for
the same pool transfer). *Status:* core behaviour, not a security matter, so it stays;
documented in the Javadoc of `onOutputReceived`. Observed on mainnet on 2026-11-07.

The outputs that `getOutputs()` returns carry no asset at all;
[getOutputs() reports no asset](06-history.md#getoutputs-reports-no-asset) shows how to get it.

## What a callback may call

From inside a callback you can read the wallet, add or remove listeners, call `stopSyncing()` and
build transactions without relaying them. The calls that cannot run there are refused before they
reach the core. Each throws a `ZephyrError` such as:

```text
Cannot call save() from inside a listener callback; call it after the callback has returned
```

The refused calls, and the reason for each, which follows from the core function the call ends in
(read in the core's code):

| Refused inside a callback | Why |
|---|---|
| every `sync`, `scanTxs`, `rescanBlockchain`, `makeMultisig`, `exchangeMultisigKeys` | the core function locks the core's sync mutex, which the refresh that delivers the callback holds: the thread would wait for itself |
| every call that relays: `createTx`, `createTxs`, `sweepUnlocked`, `sweepOutput` and `sweepDust` with relay; `relayTx`, `relayTxs`, `submitTxs`; and `importKeyImages` | the core notifies the listeners again, from inside the notification that runs |
| `submitMultisigTxHex` | it relays too, and marks the outputs it spends as spent, without a notification |
| `rescanSpent`, `importOutputs`, `importKeyImages`, `importMultisigHex` | they change the wallet's outputs while the refresh stands between two of its steps; `importMultisigHex` also runs a refresh of its own |
| `save`, `moveTo`, `changePassword`, `getData` | they persist the wallet. The core notifies a received output before it has applied the rest of that transaction, and before the block joins the wallet's chain. A save from an output callback would write a transaction half processed |
| `startSyncing` | it would start the core's sync thread from inside a sync |
| `close()`, `close(true)` | `close()` would wait for the callback that calls it; the message is "Cannot close the wallet from inside one of its own calls or listener callbacks" |

Not refused: `signMultisigTxHex`, `setDaemonConnection` and the reads. `signMultisigTxHex`
changes only the signed outputs' multisig nonces and the stored transaction keys, which a refresh
does not use. `setDaemonConnection` waits for the node request under way and points the core at
the new node, from which the paused refresh goes on. Neither locks the sync mutex or notifies.

A read is not always free of callbacks, though. A read that can include pool transactions (`getTxs`,
`getTransfers` and the like) first refreshes the core's view of the node's pool. A transaction to
the wallet found there is notified at once, on the same thread. So your listeners can be called
again from inside the callback that made the read. That read also moves the time of the core's last
pool query under the paused refresh (read in the core's code). Whether the refresh can then miss a
pool transaction is not known.

Save after `sync()` has returned, or from a thread of your own after a callback has handed the
event over. A sync and its save on one thread of yours never meet a refresh. That is the safe way
to save a wallet that stays open, because a `save()` is not exclusive with background syncing
([Calls from several threads](#calls-from-several-threads)). As advice: sync on a schedule on the
wallet's own thread instead of `startSyncing()`, and save after each sync:

```java examples/GuideThreads.java
ScheduledFuture<?> syncs = walletThread.scheduleWithFixedDelay(() -> {
  try {
    wallet.sync();
    wallet.save();  // between two syncs on this thread: no refresh runs
  } catch (Exception e) {
    logError(e);    // an exception that left the task would end the schedule
  }
}, 0, 30, TimeUnit.SECONDS);
```

Keep the `ScheduledFuture` to end the schedule before the wallet closes.

The refusals are per wallet. A callback that syncs, scans or relays **another** wallet is not
refused, but it should not do so either. Two wallets whose callbacks call each other's sync wait
for each other's sync mutex. The core also runs the other wallet's notifications inline, inside
the callback (read in the core's code).

In releases up to `2.3.0-sec.2-java.4` none of these calls was refused. A `sync()` from inside a
callback hung its thread on the core's sync mutex (read in the core's code). Observed on
2026-11-18 with a development build that did not refuse it: `sync()` from the first
`onSyncProgress` hung. After 30 s it had not returned, in a foreground sync and in background
syncing alike.

## An exception thrown in a callback

In `2.3.0-sec.2-java.5` nothing that a listener throws leaves the library's side of the callback.
Each listener runs in its own protection:

- a `Throwable` from your listener is logged with `java.util.logging` at `WARNING`, on the logger
  `zephyr.wallet.ZephyrWalletFull`. The record names the listener's class and the callback, and
  carries the `Throwable` itself, whose message and stack trace come from your code;
- the callback's data (amounts, hashes) is not logged;
- the other listeners are still called, and the core's notifications go on;
- callback data that cannot be read is logged with its exception's class only, and that
  notification is dropped;
- a listener that calls the wallet while `close()` runs gets "Wallet is closed", logged at `FINE`.

In the closing tests of 2026-11-18 and 2026-11-19, listeners threw from `onNewBlock`,
`onSyncProgress` or `onBalancesChanged` during background syncing. The JVM exited normally every
time, also under `-Xcheck:jni`.

Still catch inside your callbacks. A listener that throws on every block fills the log with one
`WARNING` per call, and the failure is better handled where it happens. Even the hand-over can
throw: `submit` throws a `RejectedExecutionException` once the executor is shut down.

```java examples/GuideThreads.java
wallet.addListener(new ZephyrWalletListener() {
  @Override
  public void onNewBlock(long height) {
    try {
      events.submit(() -> showHeight(height));
    } catch (Exception e) {  // nothing may leave the callback
      logError(e);
    }
  }
});
```

In releases up to `2.3.0-sec.2-java.4` an exception that left a callback could keep the process
from exiting ([Releases up to java.4](#releases-up-to-java4)).

## Closing a wallet that syncs

In `2.3.0-sec.2-java.5`, `close()` returns only when the wallet is closed:

1. it marks the wallet closed: new calls fail with "Wallet is closed", and callbacks return at
   once without reaching your listeners;
2. it asks the core to stop syncing, again and again until the core's sync thread has ended;
3. it waits, without a time limit, until no other thread is inside a call of this wallet and the
   core's background sync thread has ended;
4. it frees the native wallet.

An interrupt does not end the wait; the thread's interrupt status is restored afterwards. A second
thread that closes the wallet meanwhile waits for the first, and closing a closed wallet has no
effect.

**A call cut short by `close()` throws.** A `sync()`, `rescanBlockchain()`, `scanTxs()` or
`importMultisigHex()` that `close()` stops throws a `ZephyrError` "Wallet is closed". It never
returns a result that looks complete. Observed on 2026-11-19 in 10 of 10 runs: a `close()` from a
second thread at the first progress of a foreground `sync()` ended that sync with "Wallet is
closed". The process exited normally.

**How long `close()` waits.** A refresh normally ends within its current batch of blocks. In the
runs of 2026-11-19 on Linux against a mainnet node every `close()` returned within 203 ms. Only
the tests that held a refresh or a node request on purpose took longer. Longer waits:

- a request to the node that is under way is waited for. Against a node that does not answer,
  that is up to the core's timeout of 3 min 30 s. Observed on 2026-11-19: 208 s, with another
  thread inside `setDaemonConnection` against a socket that accepted and never answered;
- the request with which the core saves the ring members after a refresh is waited for as well;
- a listener callback that blocks is waited for;
- the rest of a sync period (10 s by default), read in the core's code and not seen in a run. That
  happens when the core's sync thread is about to wait for its next period as `close()` begins;
- a relay that hangs, read in the core's code and not seen in a run. A call that relays while a
  sync runs on another thread can hang when the core recycles its notification queue at the end
  of that sync. Nothing drains the queue after `stopSyncing()`, so the relay never returns and
  `close()` waits for it. The window is narrow (read in the code, not measured). Relaying while no
  sync runs on another thread does not meet it.

On Android, never call `close()` on the main thread: a wait of seconds is an "Application Not
Responding" dialog there. The Android emulator ran closing scenarios against the same node on
2026-11-19 and 2026-11-20. There `close()` waited 3 ms to 1.2 s with the bridge of
`2.3.0-sec.2-java.5`, and once 9.7 s. That wait, on 2026-11-20, was a `close()` during busy
background syncing, and the callbacks did not advance while it lasted. With earlier bridges
`close()` waited up to 3.5 s. So a `close()` on Android can take seconds
([What was tested](13-known-limitations.md#what-was-tested)).

**`close(true)`** saves after the core's background sync thread has ended and while no other call
runs, so the save never meets a refresh. When the save fails, the wallet is closed and freed all
the same. `close(true)` then throws a `ZephyrError` "Wallet was closed, but saving it failed: "
with the core's reason ([Closing](02-wallets.md#closing)).

With one thread per wallet ([Calls from several threads](#calls-from-several-threads)), the close
goes through that thread too:

```java examples/GuideThreads.java
syncs.cancel(false);  // no sync after the close; a sync under way ends first
return walletThread.submit(() -> wallet.close(true));
```

## Calls from several threads

Every call of `ZephyrWalletFull` that uses the native wallet runs inside a lock that `close()`
waits for. So `close()` from one thread while other threads call the wallet is safe: the calls
either finish first or fail with "Wallet is closed". Observed on 2026-11-19, offline, in 100
rounds: `close()` against a thread that called the wallet in a loop. Each round ended in "Wallet
is closed" and nothing else.

The calls among themselves are a different matter. The core serialises only syncing, so the
library makes no promise for two threads that call one wallet at the same time. Two cases are
known from the core's code:

- **A save is not exclusive with background syncing.** `save()`, `moveTo()`, `changePassword()`
  and `getData()` read or write the wallet that the core's sync thread may be refreshing. That
  holds while `startSyncing()` runs, and after `stopSyncing()` until the core's sync thread has
  finished its current sync. No call reports when it has. `close(true)` is the only save that is
  guaranteed not to meet a refresh. A `save()` on one thread during a `sync()` on another is the
  same race.
- **Change the node while no sync runs.** `setDaemonConnection` resets fields of the core that a
  refresh on another thread reads and writes without a lock. The request under way is not
  replaced: the core waits for it.

Observed on 2026-11-17 with `java.4`: two threads each called `getBalance()`, `getTxs()` and
`getHeight()` 1,000 times on one wallet while a third thread called `sync()` in a loop. There was no
exception, no crash and no inconsistent result, in two runs. **This proves nothing about thread
safety**: two runs on one machine say nothing about other timings, and writes were not run at the
same time.

The advice is one thread per wallet. Every call to the wallet, the syncs and `close()` included,
goes through one single-thread executor. A read then waits until a running `sync()` has returned,
and a save never meets a refresh.

## Close every wallet before the process ends

> [!CAUTION]
> **Close every wallet before the process ends.** A process can hang in its exit while background
> syncing refreshes. Close each wallet with `close()` or `close(true)` first; the library adds no
> shutdown hook that would do it for you.

Observed on 2026-11-18 with a development build from before the later closing fixes: a program
whose `main` returned with a wallet open and background syncing inside a refresh did not exit. That
happened in 20 of 20 runs with a listener, and in a probe without one. It did not crash; it hung in
the JVM's exit. When `main` returned while the core's sync thread waited between two syncs, the
program exited normally. This was not repeated on the final code of `2.3.0-sec.2-java.5`. Whether
`System.exit()` hangs the same way on the JVM was not run.

On Android an app's process is normally killed rather than exited. On the Android emulator,
killing and exiting an app's process with a wallet syncing in the background ran with two kinds of
AAR. On 2026-11-19 it ran with AARs from before the last change to the JNI bridge, on API 24 and on
API 35 with 16 KB pages. On 2026-11-20 it ran with the bridge of `2.3.0-sec.2-java.5`, in a
minified app, three times each on every image. The images were API 24, API 35 and API 36 with
4 KB pages, and API 36 with 16 KB pages. Such a process ended when the system or `am force-stop`
killed it. `System.exit(0)` ended it with exit code 0, within 0.2 s with the earlier AARs and
within 0.5 s with the bridge of `2.3.0-sec.2-java.5`. None of these runs left a crash or a
tombstone.

## What is not known

For `2.3.0-sec.2-java.5`, none of these was established:

- whether calls from several threads at the same time are safe, apart from `close()`;
- in particular, writes (`createTxs`, `setTxNote`, creating accounts) next to other calls;
- what the first `addListener` does while a sync runs on another thread (the core's race above);
- whether a read from a callback that refreshes the pool can make the paused refresh miss a pool
  transaction;
- the two longer waits of `close()` that were read in the core's code;
- how this behaves on Android hardware: the closing tests ran on the Android emulator only
  ([What was tested](13-known-limitations.md#what-was-tested));
- listener callbacks and the closing tests with the bridge of `2.3.0-sec.2-java.5` under ART's
  CheckJNI: the debug app's tests that ran this bridge add no listener;
- `close()` while the app's process is being ended.

[Known limitations](13-known-limitations.md) lists what remains, and what `2.3.0-sec.2-java.5`
fixed.

## Releases up to java.4

Releases up to `2.3.0-sec.2-java.4` had the defects that `2.3.0-sec.2-java.5` fixed. Observed on
2026-11-17 with the released `java.4` JAR:

- **`close()` during a `sync()` on another thread crashed the process**, in 4 of 4 runs, with a
  segmentation fault (exit status 134). The crash point differed in each run, so it can also
  corrupt memory without a crash;
- **`close()` while background syncing scanned** left a native thread attached, and the JVM never
  exited (3 of 3 runs). `stopSyncing()` right before `close()` did not help (2 of 2 runs);
- **an exception that left a callback during background syncing** left a native thread attached,
  and the JVM never exited (3 of 3 runs). During a foreground `sync()` the exception was printed
  on stderr, and the rest of that block's notifications was skipped. That run used a developer
  build of the `java.4` sources.

On those releases: catch every exception inside a callback, let `sync()` return before
`close()`, and do not call `close()` while background syncing scans. `stopSyncing()` only clears a
flag, and the batch of blocks in progress still runs. There is no call that reports whether a scan
still runs. The most defensible workaround: call `stopSyncing()`, wait until no callback has
arrived for a while, then save and close. A listener added before `startSyncing()` notes the time
of the last callback:

```java examples/GuideThreads.java
wallet.stopSyncing();  // returns at once; the batch in progress goes on
while (System.currentTimeMillis() - lastCallbackMillis.get() < quietMillis) {
  Thread.sleep(100);   // no call reports whether a scan still runs, so wait for quiet
}
wallet.save();
wallet.close();
```

No quiet interval is known to be safe. A scan can pause between batches while it fetches blocks
from the node, so a short interval can still meet a running batch. In the runs of 2026-11-17,
`close()` 3 s after `stopSyncing()` was clean in 4 of 4 runs; that is what was run, not a safe
interval. This is a workaround, not a guarantee.
