# 2. Wallets

`ZephyrWalletFull` is the wallet that runs in your process. Every wallet is created or opened
through one of its static methods and ends with `close()`. This chapter goes through the life of
a wallet: creating and restoring it, view-only wallets, files and bytes, the password, saving and
closing, addresses and keys. Connecting it to a node and syncing are in
[Connecting to a node](03-connecting-to-a-node.md).

The blocks marked "Java, compiled" in this chapter come from
`src/test/java/examples/GuideWallets.java`.

## Network types

Every example uses mainnet (`ZephyrNetworkType.MAINNET`). A config without a network type is
refused with "Must specify a network type: 'mainnet', 'testnet' or 'stagenet'".

**Testnet and stagenet do not reach the V2 rules.** Zephyr's testnet and stagenet exist in the
code. In the pinned Zephyr tree their hard-fork tables stop at hard fork 3 (height 100), and the
V2 asset names are not allowed before hard fork 8. A wallet on those networks never sees ZPH,
ZSD, ZRS or ZYS. Development and tests that need the current rules run on mainnet with small
amounts. *Status:* the state of the core's network tables.

## Creating a wallet

A new random wallet needs a network type and, to be stored as files, a path and a password:

```java examples/GuideWallets.java
ZephyrWalletFull wallet = ZephyrWalletFull.createWallet(new ZephyrWalletConfig()
    .setPath("wallets/main")
    .setPassword(password)
    .setNetworkType(ZephyrNetworkType.MAINNET)
    .setServerUri("http://node.example.invalid:17767"));
String mnemonic = wallet.getMnemonic();  // 25 words; show it to the user once, then forget it
```

Replace `node.example.invalid` with your node's host name; `17767` is the default RPC port of a
mainnet node. Without a path the wallet lives only in memory ([Wallet bytes](#wallet-bytes)).

A random wallet does not take a restore height: `createWallet` rejects one with "Cannot specify
restore height when creating random wallet". The core does not need one either. When the new
wallet is connected to a node, the core sets its restore height to the node's current height. A
new wallet cannot have received anything before it existed.
`createWallet` refuses a path where a wallet already exists ("Wallet already exists: " and the
path).

> [!CAUTION]
> **The mnemonic is the wallet.** Anyone with the 25 words of `getMnemonic()` can spend every
> asset of the wallet. Do not log them, store them in plain text or send them anywhere. A screen
> that shows them can tell the user: "Anyone with these 25 words can spend all funds in this
> wallet across every asset (ZPH, ZSD, ZRS, ZYS). Write the words down in order on paper and store
> them offline. Do not save a screenshot, photo, or plain-text file on an internet-connected
> device."

## Restoring from a mnemonic

To restore, give the mnemonic and a restore height: the block height from which the wallet scans
for its outputs. Without one the restore height is 0 and the first sync scans the whole chain,
which takes a long time. A height a little before the wallet's first transaction is the right
value. `wallet.getHeightByDate(year, month, day)` asks the node for the height at a date.

```java examples/GuideWallets.java
ZephyrWalletFull restored = ZephyrWalletFull.createWallet(new ZephyrWalletConfig()
    .setPath("wallets/restored")
    .setPassword(password)
    .setNetworkType(ZephyrNetworkType.MAINNET)
    .setMnemonic(mnemonic)
    .setRestoreHeight(536000L)
    .setServerUri(nodeUri));
```

`ZephyrWalletFull.getMnemonicLanguages()` lists the languages of the core's word lists. Seed
offsets (`setSeedOffset`) and languages (`setLanguage`) are inherited from monero-java; not
tested against Zephyr.

## View-only wallets

A wallet can also be built from keys. `setPrimaryAddress` with `setPrivateViewKey` gives a
view-only wallet that sees incoming funds but cannot spend. Adding `setPrivateSpendKey` gives a
full wallet. A mnemonic and keys together are rejected ("Wallet may be initialized with a mnemonic
or keys but not both"). `wallet.isViewOnly()` tells the two kinds apart.

```java examples/GuideWallets.java
ZephyrWalletFull viewOnly = ZephyrWalletFull.createWallet(new ZephyrWalletConfig()
    .setPassword(password)
    .setNetworkType(ZephyrNetworkType.MAINNET)
    .setPrimaryAddress(address)
    .setPrivateViewKey(privateViewKey)
    .setRestoreHeight(restoreHeight)
    .setServerUri(nodeUri));
```

**A view-only wallet's balance is not the real balance once the wallet has spent anything.** A
view-only wallet sees every incoming output but none of the spends. Observed on 2026-11-17 with a
test wallet that had received five outputs and spent all five:

| After a full sync | Full wallet | View-only wallet |
|---|---|---|
| `isViewOnly()` | false | true |
| `getBalance()` and `getUnlockedBalance()` | 0 in every asset | 232.9989266 ZPH and 95.792897912645 ZRS |
| `getTxs()` | 5 (3 incoming, 4 outgoing) | 4 (4 incoming, 0 outgoing) |
| `getOutputs(new ZephyrOutputQuery())` | 5, all spent | 5, none spent |
| `getTransfers()` | 7 | 4 |
| `getPrivateSpendKey()`, `getMnemonic()` | the keys | `null`, `null` |

So the view-only balance is the sum of everything ever received, every output shows as unspent
and outgoing transactions are missing. Importing key images, which would let it see the spends,
is inherited from monero-java; not tested against Zephyr. An app that shows a view-only wallet
can say so on screen, for example: "View-only wallet. This wallet contains only your view key; it
can monitor incoming transactions but cannot sign outgoing transfers or conversions, and cannot
verify spent outputs without key images."

## Opening wallet files

A wallet created with a path is stored as two files: `<path>.keys` (the keys, encrypted with the
password) and `<path>` (the cache: the synced transactions and outputs). Open them with
`openWallet`:

```java examples/GuideWallets.java
if (ZephyrWalletFull.walletExists(path)) {
  try {
    wallet = ZephyrWalletFull.openWallet(path, password, ZephyrNetworkType.MAINNET, nodeUri);
  } catch (Exception e) {  // "invalid password" arrives as java.lang.Exception
    messages.show("The wallet could not be opened: " + e.getMessage());
  }
}
```

The network type must match the one the wallet was created with. A path without a wallet throws
a `ZephyrError` "Wallet does not exist at path: " with the path.

**A wrong password is not a `ZephyrError`.** Observed on 2026-11-17: `openWallet` with a wrong
password threw `java.lang.Exception` with the message "invalid password". A `catch (ZephyrError
e)` does not catch it; catch `Exception` around `openWallet`
([Errors and exceptions](09-errors.md)).

## Wallet bytes

Apps that keep their own storage (an encrypted database, Android's private files) need no wallet
files at all. `getData()` returns the keys and the cache as two byte arrays:

```java examples/GuideWallets.java
byte[][] data = wallet.getData();        // data[0] = keys, data[1] = cache
store.put("keys", data[0]);
store.put("cache", data[1]);
```

`openWalletData` opens a wallet from them:

```java examples/GuideWallets.java
ZephyrWalletFull wallet = ZephyrWalletFull.openWalletData(password, ZephyrNetworkType.MAINNET,
    store.get("keys"), store.get("cache"), new ZephyrRpcConnection(nodeUri));
```

`ZephyrWalletFull.openWallet(ZephyrWalletConfig)` does the same when the config carries the
arrays:

```java examples/GuideWallets.java
ZephyrWalletFull wallet = ZephyrWalletFull.openWallet(new ZephyrWalletConfig()
    .setPassword(password)
    .setNetworkType(ZephyrNetworkType.MAINNET)
    .setKeysData(store.get("keys"))
    .setCacheData(store.get("cache"))
    .setServerUri(nodeUri));
```

The keys array is encrypted with the wallet password. The cache array, which holds the synced
history, is encrypted with a key derived from it. The files are encrypted the same way.

A wallet opened this way has no path (`getPath()` returns `null`), so `save()` has nowhere to
write. Call `getData()` again and store the result whenever you would otherwise save.
`changePassword` does not work for such a wallet; [The password](#the-password) has the details.

`moveTo(path)` gives a wallet held in memory its files:

```java examples/GuideWallets.java
wallet.moveTo("wallets/main");
wallet.save();  // writes to the new files from now on
```

Observed on 2026-11-17 with a wallet opened from bytes: `moveTo` wrote `<path>` (the cache) and
`<path>.keys`, and no `.address.txt`. Then `getPath()` returned the new path, `save()` worked, and
the wallet opened again from that path at the same height. Moving onto an existing file, into a
missing directory or onto another file system was not tried.

## The password

**`changePassword` fails for a wallet opened from bytes.** On a wallet opened with
`openWalletData` (or created without a path), `changePassword` throws
`failed to read file ".keys"`. The core verifies the old password against the keys file on disk,
which such a wallet does not have. The same call on a wallet with files is not affected. No stray
file is written. *Status:* core behaviour (the core took only half of a later monero-cpp fix),
confirmed on mainnet. Apps that store bytes can keep the password stable, or restore the
wallet from its mnemonic under the new password (which costs a re-sync).

A wrong old password is refused before the core is asked, with a `ZephyrError` "Invalid original
password.".

## When to save

`save()` writes the cache (and the keys) to the wallet's files. A cache that was not saved only
costs a re-sync of the blocks since the last save. But transaction notes and the secret keys of
sent transactions exist only in the cache. Save, or with bytes call `getData()` and store the
arrays again:

- after each sync;
- after each send;
- after `setTxNote` and other changes the wallet keeps for you;
- before your process can be stopped, on Android when the app goes to the background
  ([Lifecycle and threads](10-android.md#lifecycle-and-threads)).

## Closing

`close()` closes without saving; `close(true)` saves first. After `close` the object must not be
used again: `isClosed()` returns `true`, and the other calls throw a `ZephyrError` "Wallet is
closed". Closing a closed wallet has no effect.

```java examples/GuideWallets.java
try {
  wallet.sync();
  System.out.println(wallet.getBalance());
} finally {
  wallet.close(true);
}
```

Never call `close()` while another thread may be inside `sync()`, and end background syncing
before you close. Observed on 2026-11-17, closing during a sync crashed the JVM
([Closing a wallet that syncs](04-threads-and-listeners.md#closing-a-wallet-that-syncs)).

## Addresses and keys

| Call | Returns |
|---|---|
| `getPrimaryAddress()` | the address of account 0, subaddress 0 |
| `getAddress(accountIdx, subaddressIdx)` | the address of a subaddress |
| `getAddressIndex(address)` | the account and subaddress of one of the wallet's addresses |
| `getMnemonic()` | the 25-word seed phrase; `null` for a view-only wallet (observed on 2026-11-17) |
| `getPrivateViewKey()`, `getPublicViewKey()` | the view keys |
| `getPrivateSpendKey()`, `getPublicSpendKey()` | the spend keys; the private one is `null` for a view-only wallet (observed on 2026-11-17) |

Mainnet addresses start with `ZEPHYR`. Check an address the user enters before you send to it:

```java examples/GuideWallets.java
return ZephyrUtils.isValidAddress(recipient, ZephyrNetworkType.MAINNET);
```

`isValidAddress` and `validateAddress` decode the address in Java and need no native library.
`validateAddress` throws a `ZephyrError` that names the problem, for example "Address has invalid
characters", "Address has invalid encoding", "Address has invalid length" or "Address has invalid
hash". An address of another network gives "Address network type mismatch".

The spend key and the mnemonic give full control over every asset of the wallet. An app that
exports them can warn the user: "Your secret spend key grants full control over every asset in
this wallet. Export it only when migrating to another offline wallet client that you have
verified."
