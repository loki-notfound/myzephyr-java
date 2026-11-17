# 4. Threads and listeners

Wallet calls block, and listener callbacks come from the core. This chapter covers the threads
both run on and the five callbacks. It then covers exceptions in callbacks, closing a wallet that
syncs, calls from several threads, and what is not known.

The blocks marked "Java, compiled" in this chapter come from
`src/test/java/examples/GuideThreads.java`. Statements marked "observed on 2026-11-17" come from
runs on Linux `x86_64`, some with the released JAR, none on Android
([Where the statements come from](README.md#where-the-statements-come-from)).

## Calls block

`sync()`, `createTxs()`, `getTxs()` and the other wallet methods do their work on the calling
thread and return when the core is done. A first sync of a restored wallet can take minutes, and
building a transaction asks the node for decoy outputs. Never call them from a thread that must
stay responsive, such as Android's main thread. A sync or a send on the main thread freezes the
app. Use an executor or a coroutine dispatcher for I/O.

## Callbacks run on threads you did not create

The core delivers notifications from a job of its thread pool. When that job runs on a native
thread, the JNI bridge attaches the thread to the JVM for the call. Your listener therefore runs
on a thread you did not create.

Observed on 2026-11-17: the callbacks of a foreground `sync()` ran on the thread that called
`sync()`. The callbacks of background syncing ran on native threads attached to the JVM, named
`Thread-<n>`, with a new name for each callback. Do not rely on either. Hand the event over to
your own thread (an executor, Android's main looper) and return quickly.

## Keep callbacks short

During a sync, the core hands each new block's notifications to that job and **waits for them to
finish** before it goes on. A listener that does slow work, or that calls back into the same
wallet and waits, stalls the sync. Keep callbacks short and never call the wallet from inside one;
queue the work instead:

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
`sync(listener)` is used for that sync only. Background syncing (`startSyncing()`) runs on threads
of the core as well; the same rules apply to the callbacks it triggers.
[Syncing](03-connecting-to-a-node.md#syncing) has the details of syncing.

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

## An exception thrown in a callback

> [!CAUTION]
> **Catch every exception inside a callback.** Observed on 2026-11-17 with the released JAR, in 3
> of 3 runs: an exception that left a callback during background syncing left a native thread
> attached to the JVM. The JVM then never exited. Wrap the body of every callback in `try` and
> `catch`.

What the runs of 2026-11-17 showed, for a listener that threw a `RuntimeException` from
`onSyncProgress`, `onNewBlock` or `onBalancesChanged` once:

- **During a foreground `sync()`** the exception did not reach the caller. `sync()` returned
  normally, the scan finished, and later callbacks kept arriving. The exception's stack trace was
  printed to stderr ("Exception in thread "main" ..."), and the JVM exited normally. These runs
  used the developer build.
- **During background syncing** the sync was not stopped either: it reached the node's height and
  later callbacks arrived. But the native thread that delivered the failing callback stayed
  attached to the JVM as a non-daemon thread, through `stopSyncing()` and `close()`. The JVM never
  exited when `main` returned. That happened in 3 of 3 runs with the released JAR, one per callback
  type, and in 4 of 4 with the developer build. From the throw on, every later callback came on that same
  thread.
- **The rest of that block's notifications was skipped.** A throw from `onNewBlock` cost that
  block's `onSyncProgress`. A throw from `onBalancesChanged` left the core's remembered balance
  unchanged, so the change was reported once more.
- A throw from `onBalancesChanged` raised during an incoming output also printed "Error processing
  confirmed output received: " with the message on stdout.
- With `-Xcheck:jni` (developer build) the JVM printed one warning per run and no fatal error. The
  warning was "WARNING in native method: JNI call made without checking exceptions when required
  to from CallObjectMethodV".

When the native log is switched on, it records the message after "Exception occurred in Java: "
and "Exception in threadpool job: " ([The native log](09-errors.md#the-native-log)).

Not shown by those runs: a listener that throws on every call, two listeners when one throws, and
anything on Android. By the JNI rules, a pending Java exception on a thread that keeps calling JNI
is undefined behaviour. A JVM that survived once proves nothing. Whether the leaked thread does
harm beyond blocking the JVM's exit is not known.

The rule that follows:

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

Even the hand-over can throw: `submit` throws a `RejectedExecutionException` once the executor is
shut down.

## Closing a wallet that syncs

> [!CAUTION]
> **Never close a wallet while it syncs.** Observed on 2026-11-17 with the released JAR: a
> `close()` during a `sync()` on another thread crashed the process in 4 of 4 runs. Let `sync()`
> return, and let background syncing finish its scan, before you call `close()`.

What the runs of 2026-11-17 with the released JAR showed:

- **During a foreground `sync()`**, `close()` from a second thread returned after a few
  milliseconds. The process then died with a segmentation fault in the native wallet code (exit
  status 134). That happened right after the first callback and halfway through a scan alike. The crash point
  differed in each run. A developer build of the same sources aborted with an assertion instead.
- **While background syncing was scanning**, `close()` returned (after 58 to 142 ms) in 3 of 3
  runs. While it waited, the callbacks delivered meanwhile failed in the Java bridge with a
  `ZephyrError` "Wallet is closed". Each failure printed `Exception in thread "Thread-<n>" Wallet
  is closed` on stderr, hundreds of times per run. The JVM did not crash, but a native thread stayed
  attached and the JVM never exited.
- **`stopSyncing()` right before `close()` did not help** during a busy scan (2 of 2 runs hung in
  the same way). `stopSyncing()` returns at once, but the batch of blocks in progress is still
  processed. 401 and 1,980 callbacks arrived in the 3 s after it returned.

Clean in those runs:

- `close()` after `sync()` had returned;
- `close()` 3 s after `stopSyncing()`, in 4 of 4 runs. In the two runs during a busy scan, the scan
  stopped far below the node's height (at about 904,100 and 903,101, with the node near 909,840).

The 3 s are what was run, not a safe interval. In one earlier run with the developer build,
`close()` while background syncing was idle at the node's height was clean as well.

What the code shows (read in the code, not run): `stopSyncing()` only clears a flag. The core's
refresh checks that flag once per batch of blocks, so the batch in progress still runs. A refresh
that starts at the same moment can even run in full. Java's `close()` marks the wallet closed
before the core stops and joins its sync thread. So the callbacks of a batch still in progress
fail with "Wallet is closed", as the runs above showed.

Not shown by those runs: a close that coincides with a background refresh of a synced wallet when
a new block arrives. That the crash is always a segmentation fault is not shown either. A crash
point that differs per run can also corrupt memory without a crash.

So, for a foreground sync, let `sync()` return before `close()`. One thread per wallet makes that
easy: when every call goes through the same single-thread executor, `close()` cannot run while
`sync()` runs:

```java examples/GuideThreads.java
return walletThread.submit(() -> {
  wallet.save();   // runs after a sync() submitted earlier has returned
  wallet.close();
});
```

**For background syncing there is no clean signal.** `2.3.0-sec.2-java.4` has no call that reports
whether a scan is still running, and a stopped scan can end far below the node's height. The most
defensible workaround the facts support: call `stopSyncing()`, wait until no callback has arrived
for a while, then save and close. A listener added before `startSyncing()` notes the time of the
last callback:

```java examples/GuideThreads.java
wallet.stopSyncing();  // returns at once; the batch in progress goes on
while (System.currentTimeMillis() - lastCallbackMillis.get() < quietMillis) {
  Thread.sleep(100);   // no call reports whether a scan still runs, so wait for quiet
}
wallet.save();
wallet.close();
```

No quiet interval is known to be safe. A scan can pause between batches while it fetches blocks
from the node, so a short interval can still meet a running batch. This is a workaround, not a
guarantee.

## Calls from several threads

In Java, only `getData()` of `ZephyrWalletFull` is `synchronized`, and the core serialises only
syncing. So the library makes no promise for two threads that call one
wallet at the same time.

Observed on 2026-11-17: two threads each called `getBalance()`, `getTxs()` and `getHeight()` 1,000
times on one wallet while a third thread called `sync()` in a loop. There was no exception, no
crash and no inconsistent result, in two runs. **This proves nothing about thread safety**: two
runs on one machine say nothing about other timings, and writes were not run at the same time.

The advice is one thread per wallet, as in the closing example above. Every call to the wallet,
`close()` included, goes through one single-thread executor. A read then waits until a running
`sync()` has returned. Background syncing still runs on the core's threads; its callbacks follow
the rules above.

## What is not known

For `2.3.0-sec.2-java.4`, none of these was established:

- whether calls from several threads at the same time are safe;
- in particular, writes (`createTxs`, `setTxNote`, `save`, `moveTo`, creating accounts) next to
  other calls, and `close()` next to reads;
- what a listener that keeps throwing does, and whether a throw skips other listeners;
- a reliable way to know that a background scan has stopped, and so when a `close()` after
  `stopSyncing()` is safe;
- what a `close()` during a background refresh of a synced wallet does;
- how any of this behaves on Android (ART, and CheckJNI in debuggable apps).

[Known limitations](13-known-limitations.md) lists the crash and the threads that stay attached.
