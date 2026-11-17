# 4. Threads and listeners

Wallet calls block, and listener callbacks come from the core. This chapter covers the threads
both run on and the five callbacks. It then covers exceptions in callbacks, closing a wallet that
syncs, calls from several threads, and what is not known.

The blocks marked "Java, compiled" in this chapter come from
`src/test/java/examples/GuideThreads.java`. Statements marked "observed on 2026-11-17" come from
runs on Linux `x86_64`, not on Android
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
| `onOutputReceived(output)` | an output for the wallet appeared, in the pool or in a block | amount, account, subaddress, tx hash, height, `assetType` |
| `onOutputSpent(output)` | an output of the wallet was spent in a block | the same fields, with the asset |

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
the same pool transfer). ZephyrProtocol's own Zephyr wallets do the same and leave
`onOutputReceived` unused. *Status:* core behaviour, not a security matter, so it stays;
documented in the Javadoc of `onOutputReceived`. Observed on mainnet on 2026-11-07.

The outputs that `getOutputs()` returns carry no asset at all;
[getOutputs() reports no asset](06-history.md#getoutputs-reports-no-asset) shows how to get it.

## An exception thrown in a callback

> [!CAUTION]
> **Catch every exception inside a callback.** Observed on 2026-11-17: an exception that left a
> callback during background syncing left a native thread attached to the JVM. The JVM then did
> not exit when `main` returned. Wrap the body of every callback in `try` and `catch`.

What the runs of 2026-11-17 showed, for a listener that threw a `RuntimeException` from
`onSyncProgress`, `onNewBlock` or `onBalancesChanged` once:

- **During a foreground `sync()`** the exception did not reach the caller. `sync()` returned
  normally, the scan finished, and later callbacks kept arriving. The exception's stack trace was
  printed to stderr ("Exception in thread "main" ..."), and the JVM exited normally.
- **During background syncing** the sync was not stopped either: it reached the node's height and
  later callbacks arrived. But the native thread that delivered the failing callback stayed
  attached to the JVM as a non-daemon thread after `stopSyncing()` and `close()`. In all four such
  runs the JVM did not exit when `main` returned.
- **The rest of that block's notifications was skipped.** A throw from `onNewBlock` cost that
  block's `onSyncProgress`. A throw from `onBalancesChanged` left the core's remembered balance
  unchanged, so the change was reported once more.
- A throw from `onBalancesChanged` raised during an incoming output also printed "Error processing
  confirmed output received: " with the message on stdout.
- With `-Xcheck:jni` the JVM printed one warning per run and no fatal error. The warning was "WARNING
  in native method: JNI call made without checking exceptions when required to from
  CallObjectMethodV".

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
> **Never close a wallet while it syncs.** Observed on 2026-11-17: `close()` from a second thread
> while another thread was inside `sync()` crashed the JVM in 4 of 4 runs. Call `stopSyncing()`,
> let any running `sync()` return, then call `close()`.

What the runs of 2026-11-17 showed:

- **During a foreground `sync()`**, `close()` from a second thread aborted the process (exit code
  134, SIGABRT). It did so right after the first callback and halfway through a scan alike. The JVM writes
  no `hs_err` file for an abort. The last line was:

```text
java: /usr/include/boost/thread/pthread/mutex.hpp:56: boost::mutex::~mutex(): Assertion `!posix::pthread_mutex_destroy(&m)' failed.
```

- **While background syncing was scanning**, `close()` returned (in 208 ms and 87 ms), but the core
  kept delivering callbacks. Each one failed in the Java bridge with a `ZephyrError` "Wallet is
  closed". Each failure printed `Exception in thread "Thread-<n>" Wallet is closed` on stderr: 989
  and 329 times in the two runs. The JVM did not crash, but a native thread stayed attached and the JVM did not exit.
- **While background syncing was idle**, `close()` returned at once and the JVM exited normally.
  The wallet was synced and no new block arrived in 7 s. A `close()` after a finished scan
  behaved the same.

Not shown by those runs: a close that coincides with a background refresh of a synced wallet,
which may behave like the scanning case. Whether `stopSyncing()` waits for a refresh that is
running was not tried either. So the rule above is the best advice known, not a tested
guarantee.

One thread per wallet makes the foreground part of the rule easy. When every call to the wallet
goes through the same single-thread executor, `close()` cannot run while `sync()` runs:

```java examples/GuideThreads.java
return walletThread.submit(() -> {
  wallet.stopSyncing();  // first end background syncing
  wallet.close(true);    // runs after a sync() submitted earlier has returned
});
```

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
- whether `stopSyncing()` waits for a background refresh that is running;
- what a `close()` during a background refresh of a synced wallet does;
- how any of this behaves on Android (ART, and CheckJNI in debuggable apps).

[Known limitations](13-known-limitations.md) lists the crash and the threads that stay attached.
