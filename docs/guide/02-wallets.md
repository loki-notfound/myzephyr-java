# 2. Wallets

`ZephyrWalletFull` is the wallet that runs in your process. Every wallet is created or opened
through one of its static methods and ends with `close()`. This chapter goes through the life of
a wallet: creating or restoring it, opening it from files or from bytes, connecting it to a node,
syncing, listening, saving and closing.

Every example uses mainnet. Zephyr's testnet and stagenet exist in the code, but in the pinned
tree their hard-fork tables stop long before the V2 assets, so they do not behave like mainnet
(see [chapter 8](08-known-limitations.md)).

## Creating a new wallet

A new random wallet needs a network type and, to be stored as files, a path and a password:

```java
ZephyrWalletFull wallet = ZephyrWalletFull.createWallet(new ZephyrWalletConfig()
    .setPath("wallets/main")
    .setPassword(password)
    .setNetworkType(ZephyrNetworkType.MAINNET)
    .setServerUri("http://node.example:17767"));
String mnemonic = wallet.getMnemonic();  // 25 words; show it to the user once, then forget it
```

A random wallet does not take a restore height: `createWallet` rejects one with "Cannot specify
restore height when creating random wallet". The core does not need one either. When the new
wallet is connected to a node, the core sets its restore height to the node's current height,
because a wallet that did not exist yet cannot have received anything earlier. Without a path the
wallet lives only in memory (see "Opening from bytes" below). `createWallet` refuses a path where
a wallet already exists ("Wallet already exists").

## Restoring from a mnemonic or from keys

To restore, give the mnemonic and a restore height: the block height from which the wallet scans
for its outputs. Without one the restore height is 0 and the first sync scans the whole chain,
which takes a long time. A height a little before the wallet's first transaction is the right
value; `wallet.getHeightByDate(year, month, day)` asks the node for the height at a date.

```java
ZephyrWalletFull restored = ZephyrWalletFull.createWallet(new ZephyrWalletConfig()
    .setPath("wallets/restored")
    .setPassword(password)
    .setNetworkType(ZephyrNetworkType.MAINNET)
    .setMnemonic(mnemonic)
    .setRestoreHeight(536000L)
    .setServerUri(nodeUri));
```

A wallet can also be built from keys: `setPrimaryAddress` with `setPrivateViewKey` gives a
view-only wallet that sees incoming funds but cannot spend; adding `setPrivateSpendKey` gives a
full wallet. A mnemonic and keys together are rejected. `wallet.isViewOnly()` tells the two kinds
apart.

## Opening from files

A wallet created with a path is stored as two files: `<path>.keys` (the keys, encrypted with the
password) and `<path>` (the cache: the synced transactions and outputs). Open it with:

```java
if (ZephyrWalletFull.walletExists(path)) {
  wallet = ZephyrWalletFull.openWallet(path, password, ZephyrNetworkType.MAINNET, nodeUri);
}
```

The network type must match the one the wallet was created with.

## Opening from bytes

Apps that keep their own storage (an encrypted database, Android's private files) need no wallet
files at all. `getData()` returns the keys and the cache as two byte arrays, and
`openWalletData` opens a wallet from them:

```java
byte[][] data = wallet.getData();        // data[0] = keys, data[1] = cache
store.put("keys", data[0]);
store.put("cache", data[1]);
...
ZephyrWalletFull wallet = ZephyrWalletFull.openWalletData(password, ZephyrNetworkType.MAINNET,
    store.get("keys"), store.get("cache"), new ZephyrRpcConnection(nodeUri));
```

A wallet opened this way has no path, so `save()` has nowhere to write: call `getData()` again and
store the result whenever you would otherwise save. `changePassword` does not work for such a
wallet; [chapter 8](08-known-limitations.md) has the details.

## The node connection

The core talks to a Zephyr node (`zephyrd`) over the node's RPC port. Set the connection when you
create or open the wallet, or later:

```java
wallet.setDaemonConnection(new ZephyrRpcConnection("http://node.example:17767", user, pass));
boolean connected = wallet.isConnectedToDaemon();
```

The user name and password are only for nodes that require RPC login. 17767 is the default RPC
port of a mainnet node. A wallet without a
connection still opens: it shows the state of its last sync, but cannot sync or send.
`setProxyUri` routes the core's connection through a proxy (inherited from monero-java, meant for
Tor); it has not been tested with Zephyr.

An `http://` URI sends everything in clear text, which is fine for a node you run yourself on a
network you trust. An `https://` URI makes the core verify the node's certificate against
OpenSSL's default certificate store, and on Android that store is empty unless the app provides
one. [Chapter 6](06-android.md#tls-to-a-node) explains what to do. When verification fails, the
Java side only sees "Wallet is not connected to daemon"; the reason is in the native log, which on
Android does not reach logcat ([chapter 6](06-android.md#logs)).

## Syncing

`sync()` fetches the blocks since the wallet's height and returns when the wallet is at the tip.
It blocks, so call it on a worker thread. `sync(listener)` adds a listener for that sync only.

```java
ZephyrSyncResult result = wallet.sync(new ZephyrWalletListener() {
  @Override
  public void onSyncProgress(long height, long startHeight, long endHeight, double percentDone, String message) {
    progress.post(percentDone);   // 0.0 to 1.0
  }
});
long fetched = result.getNumBlocksFetched();
```

Progress is reported once per block fetched. A wallet that is already at the tip fetches nothing
and gets **no** progress call; do not wait for one. The same holds for a new random wallet, which
starts at the node's height.

`startSyncing()` keeps the wallet in sync in the background, every 10 seconds by default
(`startSyncing(periodInMs)` sets another period); `stopSyncing()` ends it. Listeners added with
`addListener` receive the events of background syncs too.

## The listener

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
monero-java that overrides it fails to compile instead of silently never being called. Second,
**the asset of an unconfirmed output is empty.** When an incoming payment is seen in the pool,
`onOutputReceived` arrives with `getAssetType()` equal to `""`, because the core's callback for
pool payments carries no asset. The notification for the same output once it is in a block
carries the asset (from the core's code; the mainnet test run did not get to observe it). Use
`onBalancesChanged` or `getTxs()` for the asset of a pending payment. The outputs that
`getOutputs()` returns carry no asset at all; [chapter 8](08-known-limitations.md#getoutputs-reports-no-asset)
shows how to take it from the output's transaction.

The callbacks run on a native thread; keep them short and never call the wallet from inside one
([chapter 1](01-getting-started.md#threads)).

## Saving and closing

`save()` writes the cache (and the keys) to the wallet's files. `close()` closes without saving;
`close(true)` saves first. After `close` the object must not be used again; `isClosed()`
returns `true`. Save before your process can be stopped: a cache that was not saved
only costs a re-sync of the blocks since the last save, but transaction notes and the secret keys
of sent transactions exist only in the cache.

```java
try {
  wallet.sync();
  ...
} finally {
  wallet.close(true);
}
```
