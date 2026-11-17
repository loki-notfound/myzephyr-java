# 4. Threads and listeners

This chapter covers the threads that wallet calls and listener callbacks run on, and the
callbacks of the listener.

## Threads

Two rules hold for every wallet call:

- **Calls block.** `sync()`, `createTxs()`, `getTxs()` and the other wallet methods do their work
  on the calling thread and return when the core is done. A first sync of a restored wallet can
  take minutes, and building a transaction asks the node for decoy outputs. Never call them from
  a thread that must stay responsive, such as Android's main thread: a sync or a send on the main
  thread freezes the app. Use an executor or a coroutine dispatcher for I/O.
- **Listener callbacks come in on a native thread.** The core delivers notifications from its
  own notification thread, which the JNI bridge attaches to the JVM for the call. Your listener
  therefore runs on a thread you did not create. Hand the event over to your own thread
  (an executor, Android's main looper) and return quickly.

## Keep callbacks short

The second rule has a consequence that is easy to miss. During a sync, the core hands each new
block's notifications to its notification thread and **waits for them to finish** before it goes
on. A listener that does slow work, or that calls back into the same wallet and waits, stalls the
sync. Keep callbacks short and never call the wallet from inside one; queue the work instead.

```java
ExecutorService events = Executors.newSingleThreadExecutor();
wallet.addListener(new ZephyrWalletListener() {
  @Override
  public void onBalancesChanged(BigInteger balance, BigInteger unlockedBalance, String assetType) {
    events.submit(() -> showBalance(assetType, balance, unlockedBalance));  // return at once
  }
});
```

Background syncing (`startSyncing()`) runs on a thread of the core as well; the same rules apply
to the callbacks it triggers. [Chapter 3](03-connecting-to-a-node.md#syncing) has the details of syncing.

## The five callbacks

`ZephyrWalletListener` has five callbacks; override the ones you need:

| Callback | When | What it carries |
|---|---|---|
| `onSyncProgress(height, startHeight, endHeight, percentDone, message)` | once per fetched block during a sync | the block, the range, a fraction 0.0 to 1.0 |
| `onNewBlock(height)` | once per fetched block | the block's height |
| `onBalancesChanged(balance, unlockedBalance, assetType)` | once per asset whose balance changed | the new balance and unlocked balance of that asset |
| `onOutputReceived(output)` | an output for the wallet appeared, in the pool or in a block | amount, account, subaddress, tx hash, height, `assetType` |
| `onOutputSpent(output)` | an output of the wallet was spent in a block | the same fields, with the asset |

Two Zephyr details matter here. First, `onBalancesChanged` names the asset; monero-java's
two-argument version still exists but is `final` and deprecated, so a listener ported from
monero-java that overrides it fails to compile instead of silently never being called.

## Outputs in the pool carry no asset

**The asset of an unconfirmed output notification is empty.** `onOutputReceived` for a payment
seen in the pool arrives with `getAssetType()` equal to `""`: `wallet2`'s callback for pool
payments has no asset parameter, and zephyr-cpp passes an empty string. The notification at
confirmation goes through a callback that carries the asset (from the core's code; the mainnet
test run did not get to observe it). Use `onBalancesChanged` (which names
the asset) or `getTxs()` (which reports the right asset for the same pool transfer). The official
Zephyr wallets do the same and leave `onOutputReceived` unused. *Status:* core behaviour, not a
security matter, so it stays; documented in the Javadoc of `onOutputReceived`.
Observed on mainnet on 2026-11-07.

The outputs that `getOutputs()` returns carry no asset at all;
[chapter 6](06-history.md#getoutputs-reports-no-asset) shows how to take it from the output's
transaction.
