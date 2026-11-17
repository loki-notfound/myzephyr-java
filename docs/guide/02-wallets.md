# 2. Wallets

`ZephyrWalletFull` is the wallet that runs in your process. Every wallet is created or opened
through one of its static methods and ends with `close()`. This chapter goes through the life of
a wallet: creating or restoring it, opening it from files or from bytes, connecting it to a node,
syncing, listening, saving and closing.

## Network types

Every example uses mainnet. Zephyr's testnet and stagenet exist in the code, but in the pinned
tree their hard-fork tables stop long before the V2 assets, so they do not behave like mainnet.

**Testnet and stagenet do not reach the V2 rules.** In the pinned Zephyr tree the hard-fork tables
of testnet and stagenet stop at hard fork 3 (height 100), and the V2 asset names are not allowed
before hard fork 8. A wallet on those networks never sees ZPH, ZSD, ZRS or ZYS. Development and
tests that need the current rules run on mainnet with small amounts. *Status:* the state of the
core's network tables.

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
wallet; [The password](#the-password) has the details.

The keys array is encrypted with the wallet password, and the cache array, which holds the synced
history, with a key derived from it; the files are encrypted the same way.

## The password

**`changePassword` fails for a wallet opened from bytes.** On a wallet opened with
`openWalletData` (or created without a path), `changePassword` throws
`failed to read file ".keys"`: the core verifies the old password against the keys file on disk,
which such a wallet does not have. The same call on a wallet with files is not affected. No stray
file is written. *Status:* core behaviour (the core took only half of a later monero-cpp fix),
confirmed on mainnet. Apps that store bytes can keep the password stable, or restore the
wallet from its mnemonic under the new password (which costs a re-sync).

## Saving and closing

`save()` writes the cache (and the keys) to the wallet's files. `close()` closes without saving;
`close(true)` saves first. After `close` the object must not be used again; `isClosed()`
returns `true`. Save before your process can be stopped: a cache that was not saved
only costs a re-sync of the blocks since the last save, but transaction notes and the secret keys
of sent transactions exist only in the cache. With bytes, store again after each sync and after
each send, since there is no `save()` to a file.

```java
try {
  wallet.sync();
  ...
} finally {
  wallet.close(true);
}
```
